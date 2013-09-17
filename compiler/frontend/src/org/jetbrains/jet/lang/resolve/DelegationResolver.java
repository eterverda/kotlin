/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.lang.resolve;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.impl.MutableClassDescriptor;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetDelegationSpecifier;
import org.jetbrains.jet.lang.psi.JetDelegatorByExpressionSpecifier;
import org.jetbrains.jet.lang.psi.JetTypeReference;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.TypeUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.jet.lang.diagnostics.Errors.MANY_IMPL_MEMBER_NOT_IMPLEMENTED;
import static org.jetbrains.jet.lang.resolve.OverridingUtil.OverrideCompatibilityInfo.Result.OVERRIDABLE;

public final class DelegationResolver<T extends CallableMemberDescriptor> {

    public static void generateDelegatesInAClass(
            @NotNull MutableClassDescriptor classDescriptor,
            @NotNull final BindingTrace trace,
            @NotNull JetClassOrObject jetClassOrObject
    ) {
        Callback<CallableMemberDescriptor> eagerResolveCallback = new Callback<CallableMemberDescriptor>() {
            @Nullable
            @Override
            public JetType getTypeByTypeReference(@NotNull JetTypeReference reference) {
                return trace.get(BindingContext.TYPE, reference);
            }

            @NotNull
            @Override
            public Collection<CallableMemberDescriptor> getMembersByType(@NotNull JetType type) {
                //noinspection unchecked
                return (Collection) Collections2.filter(type.getMemberScope().getAllDescriptors(),
                                                        Predicates.instanceOf(CallableMemberDescriptor.class));
            }
        };
        Set<CallableMemberDescriptor> existingMembers = classDescriptor.getAllCallableMembers();
        Collection<CallableMemberDescriptor> delegatedMembers =
                generateDelegatedMembers(jetClassOrObject, classDescriptor, existingMembers, trace, eagerResolveCallback);
        for (CallableMemberDescriptor descriptor : delegatedMembers) {
            if (descriptor instanceof PropertyDescriptor) {
                PropertyDescriptor propertyDescriptor = (PropertyDescriptor) descriptor;
                classDescriptor.getBuilder().addPropertyDescriptor(propertyDescriptor);
            }
            else if (descriptor instanceof SimpleFunctionDescriptor) {
                SimpleFunctionDescriptor functionDescriptor = (SimpleFunctionDescriptor) descriptor;
                classDescriptor.getBuilder().addFunctionDescriptor(functionDescriptor);
            }
        }
    }


    @NotNull
    public static <T extends CallableMemberDescriptor> Collection<T> generateDelegatedMembers(
            @NotNull JetClassOrObject classOrObject,
            @NotNull ClassDescriptor ownerDescriptor,
            @NotNull Collection<? extends CallableDescriptor> existingMembers,
            @NotNull BindingTrace trace,
            @NotNull Callback<T> callback
    ) {
        return new DelegationResolver<T>(classOrObject, ownerDescriptor, existingMembers, trace, callback).generateDelegatedMembers();
    }
    @NotNull private final JetClassOrObject classOrObject;
    @NotNull private final ClassDescriptor ownerDescriptor;
    @NotNull private final Collection<? extends CallableDescriptor> existingMembers;
    @NotNull private final BindingTrace trace;

    @NotNull private final Callback<T> callback;

    private DelegationResolver(
            @NotNull JetClassOrObject classOrObject,
            @NotNull ClassDescriptor ownerDescriptor,
            @NotNull Collection<? extends CallableDescriptor> existingMembers,
            @NotNull BindingTrace trace,
            @NotNull Callback<T> callback
    ) {

        this.classOrObject = classOrObject;
        this.ownerDescriptor = ownerDescriptor;
        this.existingMembers = existingMembers;
        this.trace = trace;
        this.callback = callback;
    }

    @NotNull
    private Collection<T> generateDelegatedMembers() {
        Collection<T> delegatedMembers = new HashSet<T>();
        for (JetDelegationSpecifier delegationSpecifier : classOrObject.getDelegationSpecifiers()) {
            if (!(delegationSpecifier instanceof JetDelegatorByExpressionSpecifier)) {
                continue;
            }
            JetDelegatorByExpressionSpecifier specifier = (JetDelegatorByExpressionSpecifier) delegationSpecifier;
            JetTypeReference typeReference = specifier.getTypeReference();
            if (typeReference == null) {
                continue;
            }
            JetType delegatedTraitType = callback.getTypeByTypeReference(typeReference);
            if (delegatedTraitType == null) {
                continue;
            }
            Collection<T> delegatesForTrait = generateDelegatesForOneTrait(delegatedMembers, delegatedTraitType);
            delegatedMembers.addAll(delegatesForTrait);
        }
        return delegatedMembers;
    }

    @NotNull
    private Collection<T> generateDelegatesForOneTrait(
            @NotNull Collection<T> existingDelegates,
            @NotNull JetType delegatedTraitType
    ) {
        Collection<T> result = new HashSet<T>();
        Collection<T> candidates = generateDelegationCandidates(delegatedTraitType);
        for (T candidate : candidates) {
            if (existingMemberOverridesDelegatedMember(candidate, existingMembers)) {
                continue;
            }
            //only leave the first delegated member
            if (checkClashWithOtherDelegatedMember(existingDelegates, candidate)) {
                continue;
            }

            result.add(candidate);
        }
        return result;
    }

    private boolean checkClashWithOtherDelegatedMember(
            @NotNull Collection<T> delegatedMembers,
            @NotNull T candidate
    ) {
        for (CallableMemberDescriptor alreadyDelegatedMember : delegatedMembers) {
            if (haveSameSignatures(alreadyDelegatedMember, candidate)) {
                //trying to delegate to many traits with the same methods
                PsiElement nameIdentifier = classOrObject.getNameIdentifier();
                if (nameIdentifier != null) {
                    trace.report(MANY_IMPL_MEMBER_NOT_IMPLEMENTED.on(nameIdentifier, classOrObject, alreadyDelegatedMember));
                }
                return true;
            }
        }
        return false;
    }

    private static boolean existingMemberOverridesDelegatedMember(
            @NotNull CallableMemberDescriptor candidate,
            @NotNull Collection<? extends CallableDescriptor> existingMembers
    ) {
        for (CallableDescriptor existingDescriptor : existingMembers) {
            if (haveSameSignatures(existingDescriptor, candidate)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private Collection<T> generateDelegationCandidates(@NotNull JetType delegatedTraitType) {
        Collection<T> descriptorsToDelegate = filterMembersFromSuperClassOfDelegatedTrait(delegatedTraitType);
        Collection<T> result = Lists.newArrayList();
        for (T memberDescriptor : descriptorsToDelegate) {
            if (memberDescriptor.getModality().isOverridable()) {
                Modality modality = DescriptorUtils.convertModality(memberDescriptor.getModality(), true);
                @SuppressWarnings("unchecked")
                T copy = (T) memberDescriptor.copy(ownerDescriptor, modality, memberDescriptor.getVisibility(),
                                                   CallableMemberDescriptor.Kind.DELEGATION, false);
                result.add(copy);
            }
        }
        return result;
    }

    @NotNull
    private Collection<T> filterMembersFromSuperClassOfDelegatedTrait(
            @NotNull JetType delegatedTraitType
    ) {
        final Collection<T> membersToSkip = getMembersFromClassSupertypeOfTrait(delegatedTraitType);
        return Collections2.filter(
                callback.getMembersByType(delegatedTraitType),
                new Predicate<CallableMemberDescriptor>() {
                    @Override
                    public boolean apply(@Nullable CallableMemberDescriptor descriptor) {
                        for (CallableMemberDescriptor memberToSkip : membersToSkip) {
                            if (haveSameSignatures(memberToSkip, descriptor)) {
                                return false;
                            }
                        }
                        return true;
                    }
                });
    }

    private static boolean haveSameSignatures(@NotNull CallableDescriptor memberOne, @NotNull CallableDescriptor memberTwo) {
        //isOverridableBy ignores return types
        return OverridingUtil.isOverridableBy(memberOne, memberTwo).getResult() == OVERRIDABLE;
    }

    @NotNull
    private Collection<T> getMembersFromClassSupertypeOfTrait(@NotNull JetType delegateTraitType) {
        JetType classSupertype = null;
        for (JetType supertype : TypeUtils.getAllSupertypes(delegateTraitType)) {
            if (isNotTrait(supertype.getConstructor().getDeclarationDescriptor())) {
                classSupertype = supertype;
                break;
            }
        }
        return classSupertype != null ? callback.getMembersByType(classSupertype) : Collections.<T>emptyList();
    }

    private static boolean isNotTrait(@NotNull DeclarationDescriptor descriptor) {
        if (descriptor instanceof ClassDescriptor) {
            ClassKind kind = ((ClassDescriptor) descriptor).getKind();
            return kind != ClassKind.TRAIT;
        }
        return false;
    }

    public interface Callback<T extends CallableMemberDescriptor> {
        @Nullable
        JetType getTypeByTypeReference(@NotNull JetTypeReference reference);

        @NotNull
        Collection<T> getMembersByType(@NotNull JetType type);
    }
}