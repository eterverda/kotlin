/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

package org.jetbrains.k2js.translate.intrinsic.functions.factories;

import com.google.dart.compiler.backend.js.ast.*;
import com.google.dart.compiler.util.AstUtil;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.BuiltInPropertyIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.basic.FunctionIntrinsic;
import org.jetbrains.k2js.translate.intrinsic.functions.patterns.DescriptorPredicate;
import org.jetbrains.k2js.translate.utils.JsAstUtils;
import org.jetbrains.k2js.translate.utils.TranslationUtils;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.k2js.translate.intrinsic.functions.patterns.PatternBuilder.pattern;
import static org.jetbrains.k2js.translate.utils.JsAstUtils.setQualifier;

/**
 * @author Pavel Talanov
 */
public final class StringOperationFIF extends CompositeFIF {

    @NotNull
    private static final DescriptorPredicate GET_PATTERN = pattern("String.get");

    @NotNull
    private static final FunctionIntrinsic GET_INTRINSIC = new FunctionIntrinsic() {
        @NotNull
        @Override
        public JsExpression apply(@Nullable JsExpression receiver,
                @NotNull List<JsExpression> arguments,
                @NotNull TranslationContext context) {
            assert receiver != null;
            assert arguments.size() == 1 : "String#get expression must have 1 argument.";
            //TODO: provide better way
            JsNameRef charAtReference = AstUtil.newQualifiedNameRef("charAt");
            setQualifier(charAtReference, receiver);
            return new JsInvocation(charAtReference, arguments);
        }
    };

    @NotNull
    public static final FunctionIntrinsicFactory INSTANCE = new StringOperationFIF();

    private StringOperationFIF() {
        add(GET_PATTERN, GET_INTRINSIC);
        add(pattern("jet", "String", "<get-length>"), new BuiltInPropertyIntrinsic("length"));
        add(pattern("js", "startsWith").receiverExists(true), new ContainsFunctionIntrinsic(false));
        add(pattern("js", "contains").receiverExists(true), new ContainsFunctionIntrinsic(true));
        add(pattern("js", "endsWith").receiverExists(true), new FunctionIntrinsic() {
            @NotNull
            @Override
            public JsExpression apply(
                    @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
            ) {
                assert receiver != null;
                Pair<JsExpression, JsExpression> a = TranslationUtils.wrapAsTemporaryIfNeed(receiver, context);
                Pair<JsExpression, JsExpression> b = TranslationUtils.wrapAsTemporaryIfNeed(arguments.get(0), context);
                return JsAstUtils.equality(new JsInvocation(new JsNameRef("indexOf", a.first), Arrays.asList(b.first, JsAstUtils.subtract(new JsNameRef("length", a.second), new JsNameRef("length", b.second)))), context.program().getNumberLiteral(0));
            }
        });

        add(pattern("CharSequence.<get-length>"), new BuiltInPropertyIntrinsic("length"));
    }

    private static class ContainsFunctionIntrinsic extends FunctionIntrinsic {
        private final boolean contains;

        private ContainsFunctionIntrinsic(boolean contains) {
            this.contains = contains;
        }

        @NotNull
        @Override
        public JsExpression apply(
                @Nullable JsExpression receiver, @NotNull List<JsExpression> arguments, @NotNull TranslationContext context
        ) {
            return new JsBinaryOperation(contains ? JsBinaryOperator.REF_NEQ : JsBinaryOperator.REF_EQ,
                                         new JsInvocation(new JsNameRef("indexOf", receiver), arguments),
                                         context.program().getNumberLiteral(contains ? -1 : 0));
        }
    }
}
