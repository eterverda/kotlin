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

package org.jetbrains.jet.lang.resolve.java.resolver;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.descriptors.serialization.ClassId;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.resolve.DescriptorFactory;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.jet.lang.resolve.java.DescriptorSearchRule;
import org.jetbrains.jet.lang.resolve.java.JavaClassFinder;
import org.jetbrains.jet.lang.resolve.java.JvmAbi;
import org.jetbrains.jet.lang.resolve.java.JvmAnnotationNames;
import org.jetbrains.jet.lang.resolve.java.descriptor.ClassDescriptorFromJvmBytecode;
import org.jetbrains.jet.lang.resolve.java.sam.SingleAbstractMethodUtils;
import org.jetbrains.jet.lang.resolve.java.scope.JavaClassNonStaticMembersScope;
import org.jetbrains.jet.lang.resolve.java.structure.JavaClass;
import org.jetbrains.jet.lang.resolve.java.structure.JavaMethod;
import org.jetbrains.jet.lang.resolve.kotlin.DeserializedDescriptorResolver;
import org.jetbrains.jet.lang.resolve.kotlin.KotlinClassFinder;
import org.jetbrains.jet.lang.resolve.kotlin.KotlinJvmBinaryClass;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.FqNameUnsafe;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.resolve.scopes.JetScope;
import org.jetbrains.jet.lang.resolve.scopes.RedeclarationHandler;
import org.jetbrains.jet.lang.resolve.scopes.WritableScope;
import org.jetbrains.jet.lang.resolve.scopes.WritableScopeImpl;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lang.types.TypeUtils;
import org.jetbrains.jet.lang.types.checker.JetTypeChecker;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;

import javax.inject.Inject;
import java.util.*;

import static org.jetbrains.jet.lang.resolve.DescriptorUtils.getClassObjectName;
import static org.jetbrains.jet.lang.resolve.java.DescriptorSearchRule.INCLUDE_KOTLIN_SOURCES;

public final class JavaClassResolver {
    private static final Logger LOG = Logger.getInstance(JavaClassResolver.class);

    @NotNull
    private final Map<FqNameUnsafe, ClassDescriptor> classDescriptorCache = new HashMap<FqNameUnsafe, ClassDescriptor>();

    @NotNull
    private final Set<FqNameUnsafe> unresolvedCache = new HashSet<FqNameUnsafe>();

    private JavaResolverCache cache;
    private JavaTypeParameterResolver typeParameterResolver;
    private JavaMemberResolver memberResolver;
    private JavaAnnotationResolver annotationResolver;
    private JavaClassFinder javaClassFinder;
    private JavaNamespaceResolver namespaceResolver;
    private JavaSupertypeResolver supertypesResolver;
    private JavaFunctionResolver functionResolver;
    private DeserializedDescriptorResolver deserializedDescriptorResolver;
    private KotlinClassFinder kotlinClassFinder;

    public JavaClassResolver() {
    }

    @Inject
    public void setCache(JavaResolverCache cache) {
        this.cache = cache;
    }

    @Inject
    public void setDeserializedDescriptorResolver(DeserializedDescriptorResolver deserializedDescriptorResolver) {
        this.deserializedDescriptorResolver = deserializedDescriptorResolver;
    }

    @Inject
    public void setTypeParameterResolver(JavaTypeParameterResolver typeParameterResolver) {
        this.typeParameterResolver = typeParameterResolver;
    }

    @Inject
    public void setMemberResolver(JavaMemberResolver memberResolver) {
        this.memberResolver = memberResolver;
    }

    @Inject
    public void setAnnotationResolver(JavaAnnotationResolver annotationResolver) {
        this.annotationResolver = annotationResolver;
    }

    @Inject
    public void setJavaClassFinder(JavaClassFinder javaClassFinder) {
        this.javaClassFinder = javaClassFinder;
    }

    @Inject
    public void setNamespaceResolver(JavaNamespaceResolver namespaceResolver) {
        this.namespaceResolver = namespaceResolver;
    }

    @Inject
    public void setSupertypesResolver(JavaSupertypeResolver supertypesResolver) {
        this.supertypesResolver = supertypesResolver;
    }

    @Inject
    public void setFunctionResolver(JavaFunctionResolver functionResolver) {
        this.functionResolver = functionResolver;
    }

    @Inject
    public void setKotlinClassFinder(KotlinClassFinder kotlinClassFinder) {
        this.kotlinClassFinder = kotlinClassFinder;
    }

    @Nullable
    public ClassDescriptor resolveClass(@NotNull FqName qualifiedName, @NotNull DescriptorSearchRule searchRule) {
        PostponedTasks postponedTasks = new PostponedTasks();
        ClassDescriptor classDescriptor = resolveClass(qualifiedName, searchRule, postponedTasks);
        postponedTasks.performTasks();
        return classDescriptor;
    }

    @Nullable
    public ClassDescriptor resolveClass(
            @NotNull FqName qualifiedName,
            @NotNull DescriptorSearchRule searchRule,
            @NotNull PostponedTasks tasks
    ) {
        if (isTraitImplementation(qualifiedName)) {
            return null;
        }

        ClassDescriptor builtinClassDescriptor = getKotlinBuiltinClassDescriptor(qualifiedName);
        if (builtinClassDescriptor != null) {
            return builtinClassDescriptor;
        }

        if (searchRule == INCLUDE_KOTLIN_SOURCES) {
            ClassDescriptor kotlinClassDescriptor = cache.getClassResolvedFromSource(qualifiedName);
            if (kotlinClassDescriptor != null) {
                return kotlinClassDescriptor;
            }
        }

        FqNameUnsafe fqName = javaClassToKotlinFqName(qualifiedName);
        ClassDescriptor cachedDescriptor = classDescriptorCache.get(fqName);
        if (cachedDescriptor != null) {
            return cachedDescriptor;
        }

        if (unresolvedCache.contains(fqName)) {
            return null;
        }

        return doResolveClass(qualifiedName, tasks);
    }

    @Nullable
    private static ClassDescriptor getKotlinBuiltinClassDescriptor(@NotNull FqName qualifiedName) {
        if (!qualifiedName.firstSegmentIs(KotlinBuiltIns.BUILT_INS_PACKAGE_NAME)) return null;

        List<Name> segments = qualifiedName.pathSegments();
        if (segments.size() < 2) return null;

        JetScope scope = KotlinBuiltIns.getInstance().getBuiltInsScope();
        for (int i = 1, size = segments.size(); i < size; i++) {
            ClassifierDescriptor classifier = scope.getClassifier(segments.get(i));
            if (classifier == null) return null;
            assert classifier instanceof ClassDescriptor : "Unexpected classifier in built-ins: " + classifier;
            scope = ((ClassDescriptor) classifier).getUnsubstitutedInnerClassesScope();
        }

        return (ClassDescriptor) scope.getContainingDeclaration();
    }

    private ClassDescriptor doResolveClass(@NotNull FqName qualifiedName, @NotNull PostponedTasks tasks) {
        //TODO: correct scope
        KotlinJvmBinaryClass kotlinClass = kotlinClassFinder.find(qualifiedName);
        if (kotlinClass != null) {
            //TODO: code duplication
            //TODO: it is a hackish way to determine whether it is inner class or not
            boolean isInnerClass = kotlinClass.getFile().getName().contains("$");
            ClassOrNamespaceDescriptor containingDeclaration = resolveParentDescriptor(qualifiedName, isInnerClass);
            // class may be resolved during resolution of parent
            ClassDescriptor cachedDescriptor = classDescriptorCache.get(javaClassToKotlinFqName(qualifiedName));
            if (cachedDescriptor != null) {
                return cachedDescriptor;
            }
            assert !unresolvedCache.contains(qualifiedName.toUnsafe())
                    : "We can resolve the class, so it can't be 'unresolved' during parent resolution";

            ClassId id = ClassId.fromFqNameAndContainingDeclaration(qualifiedName.toUnsafe(), containingDeclaration);
            ClassDescriptor deserializedDescriptor = deserializedDescriptorResolver.resolveClass(id, kotlinClass);
            if (deserializedDescriptor != null) {
                cache(javaClassToKotlinFqName(qualifiedName), deserializedDescriptor);
                return deserializedDescriptor;
            }
        }

        JavaClass javaClass = javaClassFinder.findClass(qualifiedName);
        if (javaClass == null) {
            cacheNegativeValue(javaClassToKotlinFqName(qualifiedName));
            return null;
        }

        if (KotlinBuiltIns.BUILT_INS_PACKAGE_FQ_NAME.equals(qualifiedName.parent())) {
            if (javaClass.findAnnotation(JvmAnnotationNames.ASSERT_INVISIBLE_IN_RESOLVER.getFqName()) != null) {
                if (ApplicationManager.getApplication().isInternal()) {
                    LOG.error("classpath is configured incorrectly:" +
                              " class " + qualifiedName + " from runtime must not be loaded by compiler");
                }
                return null;
            }
        }

        // Class may have been resolved previously by different Java resolver instance, and we are reusing its trace
        ClassDescriptor alreadyResolved = cache.getClass(javaClass);
        if (alreadyResolved != null) {
            return alreadyResolved;
        }

        //TODO: code duplication
        ClassOrNamespaceDescriptor containingDeclaration = resolveParentDescriptor(qualifiedName, javaClass.getOuterClass() != null);
        // class may be resolved during resolution of parent
        ClassDescriptor cachedDescriptor = classDescriptorCache.get(javaClassToKotlinFqName(qualifiedName));
        if (cachedDescriptor != null) {
            return cachedDescriptor;
        }
        assert !unresolvedCache.contains(qualifiedName.toUnsafe())
                : "We can resolve the class, so it can't be 'unresolved' during parent resolution";

        checkFqNamesAreConsistent(javaClass, qualifiedName);

        assert javaClass.getOriginKind() != JavaClass.OriginKind.KOTLIN_LIGHT_CLASS :
                "Trying to resolve a light class as a regular PsiClass: " + javaClass.getFqName();

        return doCreateClassDescriptor(qualifiedName, javaClass, tasks, containingDeclaration);
    }

    private void cacheNegativeValue(@NotNull FqNameUnsafe fqNameUnsafe) {
        if (unresolvedCache.contains(fqNameUnsafe) || classDescriptorCache.containsKey(fqNameUnsafe)) {
            throw new IllegalStateException("rewrite at " + fqNameUnsafe);
        }
        unresolvedCache.add(fqNameUnsafe);
    }

    private static boolean isTraitImplementation(@NotNull FqName qualifiedName) {
        // TODO: only if -$$TImpl class is created by Kotlin
        return qualifiedName.asString().endsWith(JvmAbi.TRAIT_IMPL_SUFFIX);
    }

    @NotNull
    private ClassDescriptorFromJvmBytecode doCreateClassDescriptor(
            @NotNull FqName fqName,
            @NotNull JavaClass javaClass,
            @NotNull PostponedTasks taskList,
            @NotNull ClassOrNamespaceDescriptor containingDeclaration
    ) {
        ClassDescriptorFromJvmBytecode classDescriptor =
                new ClassDescriptorFromJvmBytecode(containingDeclaration, determineClassKind(javaClass), isInnerClass(javaClass));

        cache(javaClassToKotlinFqName(fqName), classDescriptor);

        classDescriptor.setName(javaClass.getName());

        JavaTypeParameterResolver.Initializer typeParameterInitializer = typeParameterResolver.resolveTypeParameters(classDescriptor, javaClass);
        classDescriptor.setTypeParameterDescriptors(typeParameterInitializer.getDescriptors());

        List<JetType> supertypes = new ArrayList<JetType>();
        classDescriptor.setSupertypes(supertypes);
        classDescriptor.setVisibility(javaClass.getVisibility());
        classDescriptor.setModality(determineClassModality(javaClass));
        classDescriptor.createTypeConstructor();

        JavaClassNonStaticMembersScope scope = new JavaClassNonStaticMembersScope(classDescriptor, javaClass, false, memberResolver);
        classDescriptor.setScopeForMemberLookup(scope);
        classDescriptor.setScopeForConstructorResolve(scope);

        typeParameterInitializer.initialize();

        // TODO: ugly hack: tests crash if initializeTypeParameters called with class containing proper supertypes
        List<TypeParameterDescriptor> classTypeParameters = classDescriptor.getTypeConstructor().getParameters();
        supertypes.addAll(supertypesResolver.getSupertypes(classDescriptor, javaClass, classTypeParameters));

        if (javaClass.isEnum()) {
            ClassDescriptorFromJvmBytecode classObjectDescriptor = createClassObjectDescriptorForEnum(classDescriptor, javaClass);
            cache(getFqNameForClassObject(javaClass), classObjectDescriptor);
            classDescriptor.getBuilder().setClassObjectDescriptor(classObjectDescriptor);
        }

        classDescriptor.setAnnotations(annotationResolver.resolveAnnotations(javaClass, taskList));

        cache.recordClass(javaClass, classDescriptor);

        JavaMethod samInterfaceMethod = SingleAbstractMethodUtils.getSamInterfaceMethod(javaClass);
        if (samInterfaceMethod != null) {
            SimpleFunctionDescriptor abstractMethod = resolveFunctionOfSamInterface(samInterfaceMethod, classDescriptor);
            classDescriptor.setFunctionTypeForSamInterface(SingleAbstractMethodUtils.getFunctionTypeForAbstractMethod(abstractMethod));
        }

        return classDescriptor;
    }

    @NotNull
    private static ClassKind determineClassKind(@NotNull JavaClass klass) {
        if (klass.isInterface()) {
            return klass.isAnnotationType() ? ClassKind.ANNOTATION_CLASS : ClassKind.TRAIT;
        }
        return klass.isEnum() ? ClassKind.ENUM_CLASS : ClassKind.CLASS;
    }

    @NotNull
    private static Modality determineClassModality(@NotNull JavaClass klass) {
        return klass.isAnnotationType()
               ? Modality.FINAL
               : Modality.convertFromFlags(klass.isAbstract() || klass.isInterface(), !klass.isFinal());
    }

    @NotNull
    private static FqNameUnsafe getFqNameForClassObject(@NotNull JavaClass javaClass) {
        FqName fqName = javaClass.getFqName();
        assert fqName != null : "Reading java class with no qualified name";
        return fqName.toUnsafe().child(getClassObjectName(javaClass.getName()));
    }

    @NotNull
    private SimpleFunctionDescriptor resolveFunctionOfSamInterface(
            @NotNull JavaMethod samInterfaceMethod,
            @NotNull ClassDescriptorFromJvmBytecode samInterface
    ) {
        JavaClass methodContainer = samInterfaceMethod.getContainingClass();
        FqName containerFqName = methodContainer.getFqName();
        assert containerFqName != null : "qualified name is null for " + methodContainer;

        if (DescriptorUtils.getFQName(samInterface).equalsTo(containerFqName)) {
            SimpleFunctionDescriptor abstractMethod = functionResolver.resolveFunctionMutely(samInterfaceMethod, samInterface);
            assert abstractMethod != null : "couldn't resolve method " + samInterfaceMethod;
            return abstractMethod;
        }
        else {
            return findFunctionWithMostSpecificReturnType(TypeUtils.getAllSupertypes(samInterface.getDefaultType()));
        }
    }

    @NotNull
    private static SimpleFunctionDescriptor findFunctionWithMostSpecificReturnType(@NotNull Set<JetType> supertypes) {
        List<SimpleFunctionDescriptor> candidates = new ArrayList<SimpleFunctionDescriptor>(supertypes.size());
        for (JetType supertype : supertypes) {
            List<CallableMemberDescriptor> abstractMembers = SingleAbstractMethodUtils.getAbstractMembers(supertype);
            if (!abstractMembers.isEmpty()) {
                candidates.add((SimpleFunctionDescriptor) abstractMembers.get(0));
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Couldn't find abstract method in supertypes " + supertypes);
        }
        SimpleFunctionDescriptor currentMostSpecificType = candidates.get(0);
        for (SimpleFunctionDescriptor candidate : candidates) {
            JetType candidateReturnType = candidate.getReturnType();
            JetType currentMostSpecificReturnType = currentMostSpecificType.getReturnType();
            assert candidateReturnType != null && currentMostSpecificReturnType != null : candidate + ", " + currentMostSpecificReturnType;
            if (JetTypeChecker.INSTANCE.isSubtypeOf(candidateReturnType, currentMostSpecificReturnType)) {
                currentMostSpecificType = candidate;
            }
        }
        return currentMostSpecificType;
    }

    private void cache(@NotNull FqNameUnsafe fqName, @Nullable ClassDescriptor classDescriptor) {
        if (classDescriptor == null) {
            cacheNegativeValue(fqName);
        }
        else {
            ClassDescriptor oldValue = classDescriptorCache.put(fqName, classDescriptor);
            assert oldValue == null;
        }
    }

    private void checkFqNamesAreConsistent(@NotNull JavaClass javaClass, @NotNull FqName desiredFqName) {
        FqName fqName = javaClass.getFqName();
        assert desiredFqName.equals(fqName) : "Inconsistent FQ names: " + fqName + ", " + desiredFqName;
        FqNameUnsafe correctedName = javaClassToKotlinFqName(fqName);
        if (classDescriptorCache.containsKey(correctedName) || unresolvedCache.contains(correctedName)) {
            throw new IllegalStateException("Cache already contains FQ name: " + fqName.asString());
        }
    }

    @NotNull
    private ClassOrNamespaceDescriptor resolveParentDescriptor(@NotNull FqName childClassFQName, boolean isInnerClass) {
        FqName parentFqName = childClassFQName.parent();
        if (isInnerClass) {
            ClassDescriptor parentClass = resolveClass(parentFqName, INCLUDE_KOTLIN_SOURCES);
            if (parentClass == null) {
                throw new IllegalStateException("Could not resolve " + parentFqName + " required to be parent for " + childClassFQName);
            }
            return parentClass;
        }
        else {
            NamespaceDescriptor parentNamespace = namespaceResolver.resolveNamespace(parentFqName, INCLUDE_KOTLIN_SOURCES);
            if (parentNamespace == null) {
                throw new IllegalStateException("Could not resolve " + parentFqName + " required to be parent for " + childClassFQName);
            }
            return parentNamespace;
        }
    }

    // This method replaces "object" segments of FQ name to "<class-object-for-...>"
    @NotNull
    private static FqNameUnsafe javaClassToKotlinFqName(@NotNull FqName rawFqName) {
        List<Name> correctedSegments = new ArrayList<Name>();
        for (Name segment : rawFqName.pathSegments()) {
            if (JvmAbi.CLASS_OBJECT_CLASS_NAME.equals(segment.asString())) {
                assert !correctedSegments.isEmpty();
                Name previous = correctedSegments.get(correctedSegments.size() - 1);
                correctedSegments.add(DescriptorUtils.getClassObjectName(previous));
            }
            else {
                correctedSegments.add(segment);
            }
        }
        return FqNameUnsafe.fromSegments(correctedSegments);
    }

    private static boolean isInnerClass(@NotNull JavaClass javaClass) {
        return javaClass.getOuterClass() != null && !javaClass.isStatic();
    }

    @NotNull
    private ClassDescriptorFromJvmBytecode createClassObjectDescriptorForEnum(
            @NotNull ClassDescriptor containing,
            @NotNull JavaClass javaClass
    ) {
        ClassDescriptorFromJvmBytecode classObjectDescriptor = createSyntheticClassObject(containing, javaClass);

        JetType valuesReturnType = KotlinBuiltIns.getInstance().getArrayType(containing.getDefaultType());
        SimpleFunctionDescriptor valuesMethod =
                DescriptorFactory.createEnumClassObjectValuesMethod(classObjectDescriptor, valuesReturnType);
        classObjectDescriptor.getBuilder().addFunctionDescriptor(valuesMethod);

        JetType valueOfReturnType = containing.getDefaultType();
        SimpleFunctionDescriptor valueOfMethod =
                DescriptorFactory.createEnumClassObjectValueOfMethod(classObjectDescriptor, valueOfReturnType);
        classObjectDescriptor.getBuilder().addFunctionDescriptor(valueOfMethod);

        return classObjectDescriptor;
    }

    @NotNull
    private ClassDescriptorFromJvmBytecode createSyntheticClassObject(@NotNull ClassDescriptor containing, @NotNull JavaClass javaClass) {
        ClassDescriptorFromJvmBytecode classObjectDescriptor =
                new ClassDescriptorFromJvmBytecode(containing, ClassKind.CLASS_OBJECT, false);

        classObjectDescriptor.setName(getClassObjectName(containing.getName()));
        classObjectDescriptor.setModality(Modality.FINAL);
        classObjectDescriptor.setVisibility(containing.getVisibility());
        classObjectDescriptor.setTypeParameterDescriptors(Collections.<TypeParameterDescriptor>emptyList());
        classObjectDescriptor.createTypeConstructor();

        JavaClassNonStaticMembersScope scope = new JavaClassNonStaticMembersScope(classObjectDescriptor, javaClass, true, memberResolver);
        WritableScopeImpl writableScope =
                new WritableScopeImpl(scope, classObjectDescriptor, RedeclarationHandler.THROW_EXCEPTION, "Member lookup scope");
        writableScope.changeLockLevel(WritableScope.LockLevel.BOTH);
        classObjectDescriptor.setScopeForMemberLookup(writableScope);
        classObjectDescriptor.setScopeForConstructorResolve(scope);

        return classObjectDescriptor;
    }
}
