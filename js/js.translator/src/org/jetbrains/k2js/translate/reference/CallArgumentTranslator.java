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

package org.jetbrains.k2js.translate.reference;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.ValueArgument;
import org.jetbrains.jet.lang.resolve.calls.model.*;
import org.jetbrains.k2js.translate.context.TemporaryConstVariable;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.AbstractTranslator;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.utils.AnnotationsUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.k2js.translate.utils.BindingUtils.getDefaultArgument;

public class CallArgumentTranslator extends AbstractTranslator {

    @NotNull
    public static CallArgumentTranslator getArgumentTranslator(
            @NotNull ResolvedCall<?> resolvedCall,
            @Nullable JsExpression receiver,
            @NotNull TranslationContext context
    ) {
        CallArgumentTranslator argumentTranslator = new CallArgumentTranslator(resolvedCall, receiver, context);
        argumentTranslator.doTranslateArguments();
        return argumentTranslator;
    }

    public static void translateSingleArgument(
            @NotNull ResolvedValueArgument actualArgument,
            @NotNull ValueParameterDescriptor parameterDescriptor,
            @NotNull List<JsExpression> result,
            @NotNull TranslationContext context,
            boolean shouldWrapVarargInArray
    ) {
        List<ValueArgument> valueArguments = actualArgument.getArguments();
        if (actualArgument instanceof VarargValueArgument) {
            translateVarargArgument(valueArguments, result, context, shouldWrapVarargInArray);
        }
        else if (actualArgument instanceof DefaultValueArgument) {
            JetExpression defaultArgument = getDefaultArgument(context.bindingContext(), parameterDescriptor);
            result.add(Translation.translateAsExpression(defaultArgument, context));
        }
        else {
            assert actualArgument instanceof ExpressionValueArgument;
            assert valueArguments.size() == 1;
            JetExpression argumentExpression = valueArguments.get(0).getArgumentExpression();
            assert argumentExpression != null;
            result.add(Translation.translateAsExpression(argumentExpression, context));
        }
    }

    private static void translateVarargArgument(
            @NotNull List<ValueArgument> arguments,
            @NotNull List<JsExpression> result,
            @NotNull TranslationContext context,
            boolean shouldWrapVarargInArray
    ) {
        if (arguments.isEmpty()) {
            if (shouldWrapVarargInArray) {
                result.add(new JsArrayLiteral(Collections.<JsExpression>emptyList()));
            }
            return;
        }

        List<JsExpression> list;
        if (shouldWrapVarargInArray) {
            list = arguments.size() == 1 ? new SmartList<JsExpression>() : new ArrayList<JsExpression>(arguments.size());
            result.add(new JsArrayLiteral(list));
        }
        else {
            list = result;
        }
        for (ValueArgument argument : arguments) {
            JetExpression argumentExpression = argument.getArgumentExpression();
            assert argumentExpression != null;
            list.add(Translation.translateAsExpression(argumentExpression, context));
        }
    }

    @NotNull
    private final ResolvedCall<?> resolvedCall;
    @Nullable
    private final JsExpression receiver;

    private List<JsExpression> translateArguments;
    private final boolean isNativeFunctionCall;
    private boolean hasSpreadOperator = false;
    private TemporaryConstVariable cachedReceiver = null;

    private CallArgumentTranslator(
            @NotNull ResolvedCall<?> resolvedCall,
            @Nullable JsExpression receiver,
            @NotNull TranslationContext context
    ) {
        super(context);
        this.resolvedCall = resolvedCall;
        this.receiver = receiver;
        this.isNativeFunctionCall = AnnotationsUtils.isNativeObject(resolvedCall.getCandidateDescriptor());
    }


    private void doTranslateArguments() {
        List<ValueParameterDescriptor> valueParameters = resolvedCall.getResultingDescriptor().getValueParameters();
        if (valueParameters.isEmpty()) {
            translateArguments = Collections.emptyList();
            return;
        }

        List<JsExpression> result = new ArrayList<JsExpression>(valueParameters.size());
        List<ResolvedValueArgument> valueArgumentsByIndex = resolvedCall.getValueArgumentsByIndex();
        List<JsExpression> argsBeforeVararg = null;
        for (ValueParameterDescriptor parameterDescriptor : valueParameters) {
            ResolvedValueArgument actualArgument = valueArgumentsByIndex.get(parameterDescriptor.getIndex());

            if (actualArgument instanceof VarargValueArgument) {
                assert !hasSpreadOperator;

                List<ValueArgument> arguments = actualArgument.getArguments();
                hasSpreadOperator = arguments.size() == 1 && arguments.get(0).getSpreadElement() != null;

                if (isNativeFunctionCall && hasSpreadOperator) {
                    assert argsBeforeVararg == null;
                    argsBeforeVararg = result;
                    result = new SmartList<JsExpression>();
                }
            }

            translateSingleArgument(actualArgument, parameterDescriptor, result, context(), !isNativeFunctionCall && !hasSpreadOperator);
        }

        if (isNativeFunctionCall && hasSpreadOperator) {
            assert argsBeforeVararg != null;
            if (!argsBeforeVararg.isEmpty()) {
                JsInvocation concatArguments = new JsInvocation(new JsNameRef("concat", new JsArrayLiteral(argsBeforeVararg)), result);
                result = new SmartList<JsExpression>(concatArguments);
            }

            if (receiver != null) {
                cachedReceiver = context().getOrDeclareTemporaryConstVariable(receiver);
                result.add(0, cachedReceiver.reference());
            }
            else {
                result.add(0, JsLiteral.NULL);
            }
        }

        translateArguments = result;
    }

    @NotNull
    public List<JsExpression> getTranslateArguments() {
        return translateArguments;
    }

    public boolean isHasSpreadOperator() {
        return hasSpreadOperator;
    }

    @Nullable
    public TemporaryConstVariable getCachedReceiver() {
        return cachedReceiver;
    }
}
