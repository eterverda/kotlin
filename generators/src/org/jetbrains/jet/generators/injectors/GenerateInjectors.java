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

package org.jetbrains.jet.generators.injectors;

import com.intellij.openapi.project.Project;
import org.jetbrains.jet.codegen.ClassBuilderFactory;
import org.jetbrains.jet.codegen.ClassBuilderMode;
import org.jetbrains.jet.codegen.ClassFileFactory;
import org.jetbrains.jet.codegen.ScriptCodegen;
import org.jetbrains.jet.codegen.intrinsics.IntrinsicMethods;
import org.jetbrains.jet.codegen.state.GenerationState;
import org.jetbrains.jet.codegen.state.JetTypeMapper;
import org.jetbrains.jet.di.DependencyInjectorGenerator;
import org.jetbrains.jet.di.GivenExpression;
import org.jetbrains.jet.di.InjectorForTopDownAnalyzer;
import org.jetbrains.jet.lang.PlatformToKotlinClassMap;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptor;
import org.jetbrains.jet.lang.descriptors.ModuleDescriptorImpl;
import org.jetbrains.jet.lang.psi.JetImportsFactory;
import org.jetbrains.jet.lang.resolve.*;
import org.jetbrains.jet.lang.resolve.calls.CallResolver;
import org.jetbrains.jet.lang.resolve.calls.NeedSyntheticCallResolverExtension;
import org.jetbrains.jet.lang.resolve.java.JavaBridgeConfiguration;
import org.jetbrains.jet.lang.resolve.java.JavaClassFinderImpl;
import org.jetbrains.jet.lang.resolve.java.JavaDescriptorResolver;
import org.jetbrains.jet.lang.resolve.java.PsiClassFinderImpl;
import org.jetbrains.jet.lang.resolve.java.mapping.JavaToKotlinClassMap;
import org.jetbrains.jet.lang.resolve.java.resolver.*;
import org.jetbrains.jet.lang.resolve.kotlin.VirtualFileFinder;
import org.jetbrains.jet.lang.resolve.lazy.ResolveSession;
import org.jetbrains.jet.lang.resolve.lazy.ScopeProvider;
import org.jetbrains.jet.lang.types.DependencyClassByQualifiedNameResolverDummyImpl;
import org.jetbrains.jet.lang.types.expressions.ExpressionTypingServices;
import org.jetbrains.jet.lang.types.lang.KotlinBuiltIns;

import java.io.IOException;

// NOTE: After making changes, you need to re-generate the injectors.
//       To do that, you can run either this class, or /build.xml/generateInjectors task
public class GenerateInjectors {

    private GenerateInjectors() {
    }

    public static void main(String[] args) throws IOException {
        generateInjectorForTopDownAnalyzerBasic();
        generateInjectorForTopDownAnalyzerForJvm();
        generateInjectorForJavaDescriptorResolver();
        generateInjectorForTopDownAnalyzerForJs();
        generateMacroInjector();
        generateTestInjector();
        generateInjectorForJvmCodegen();
        generateInjectorForLazyResolve();
        generateInjectorForBodyResolve();
    }

    private static void generateInjectorForLazyResolve() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();
        generator.addParameter(Project.class);
        generator.addParameter(ResolveSession.class);
        generator.addParameter(ModuleDescriptor.class);
        generator.addPublicField(DescriptorResolver.class);
        generator.addPublicField(ExpressionTypingServices.class);
        generator.addPublicField(TypeResolver.class);
        generator.addPublicField(ScopeProvider.class);
        generator.addPublicField(AnnotationResolver.class);
        generator.addPublicField(QualifiedExpressionResolver.class);
        generator.addPublicField(JetImportsFactory.class);
        generator.addField(NeedSyntheticCallResolverExtension.class);
        generator.addField(false, PlatformToKotlinClassMap.class, null, new GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"));
        generator.generate("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForLazyResolve", GenerateInjectors.class);
    }

    private static void generateInjectorForTopDownAnalyzerBasic() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();
        generateInjectorForTopDownAnalyzerCommon(generator);
        generator.addField(DependencyClassByQualifiedNameResolverDummyImpl.class);
        generator.addField(NamespaceFactoryImpl.class);
        generator.addParameter(PlatformToKotlinClassMap.class);
        generator.generate("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForTopDownAnalyzerBasic", GenerateInjectors.class);
    }

    private static void generateInjectorForTopDownAnalyzerForJs() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();
        generateInjectorForTopDownAnalyzerCommon(generator);
        generator.addField(DependencyClassByQualifiedNameResolverDummyImpl.class);
        generator.addField(NamespaceFactoryImpl.class);
        generator.addField(false, PlatformToKotlinClassMap.class, null, new GivenExpression("org.jetbrains.jet.lang.PlatformToKotlinClassMap.EMPTY"));
        generator.generate("js/js.translator/src", "org.jetbrains.jet.di", "InjectorForTopDownAnalyzerForJs", GenerateInjectors.class);
    }

    private static void generateInjectorForTopDownAnalyzerForJvm() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();
        generator.implementInterface(InjectorForTopDownAnalyzer.class);
        generateInjectorForTopDownAnalyzerCommon(generator);
        generator.addPublicField(JavaBridgeConfiguration.class);
        generator.addField(JavaDescriptorResolver.class);
        generator.addField(PsiClassFinderImpl.class);
        generator.addField(false, JavaToKotlinClassMap.class, null, new GivenExpression("org.jetbrains.jet.lang.resolve.java.mapping.JavaToKotlinClassMap.getInstance()"));
        generator.addField(JavaClassFinderImpl.class);
        generator.addField(TraceBasedExternalSignatureResolver.class);
        generator.addField(TraceBasedJavaResolverCache.class);
        generator.addField(TraceBasedErrorReporter.class);
        generator.addField(PsiBasedMethodSignatureChecker.class);
        generator.addField(PsiBasedExternalAnnotationResolver.class);
        generator.addPublicField(NamespaceFactoryImpl.class);
        generator.addField(false, VirtualFileFinder.class, "virtualFileFinder",
                           new GivenExpression(
                                   "com.intellij.openapi.components.ServiceManager.getService(project, VirtualFileFinder.class)"));
        generator.generate("compiler/frontend.java/src", "org.jetbrains.jet.di", "InjectorForTopDownAnalyzerForJvm",
                           GenerateInjectors.class);
    }

    private static void generateInjectorForJavaDescriptorResolver() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();

        // Parameters
        generator.addParameter(Project.class);
        generator.addParameter(BindingTrace.class);

        // Fields
        generator.addField(JavaClassFinderImpl.class);
        generator.addField(TraceBasedExternalSignatureResolver.class);
        generator.addField(TraceBasedJavaResolverCache.class);
        generator.addField(TraceBasedErrorReporter.class);
        generator.addField(PsiBasedMethodSignatureChecker.class);
        generator.addField(PsiBasedExternalAnnotationResolver.class);
        generator.addPublicField(JavaDescriptorResolver.class);
        generator.addPublicField(PsiClassFinderImpl.class);
        generator.addField(false, VirtualFileFinder.class, "virtualFileFinder",
                           new GivenExpression("com.intellij.openapi.components.ServiceManager.getService(project, VirtualFileFinder.class)"));

        generator.generate("compiler/frontend.java/src", "org.jetbrains.jet.di", "InjectorForJavaDescriptorResolver",
                           GenerateInjectors.class);
    }

    private static void generateInjectorForTopDownAnalyzerCommon(DependencyInjectorGenerator generator) {
        // Fields
        generator.addPublicField(TopDownAnalyzer.class);
        generator.addPublicField(TopDownAnalysisContext.class);
        generator.addPublicField(BodyResolver.class);
        generator.addPublicField(ControlFlowAnalyzer.class);
        generator.addPublicField(DeclarationsChecker.class);
        generator.addPublicField(DescriptorResolver.class);
        generator.addField(NeedSyntheticCallResolverExtension.class);

        // Parameters
        generator.addPublicParameter(Project.class);
        generator.addPublicParameter(TopDownAnalysisParameters.class);
        generator.addPublicParameter(BindingTrace.class);
        generator.addPublicParameter(ModuleDescriptorImpl.class);
    }

    private static void generateMacroInjector() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();

        // Fields
        generator.addPublicField(ExpressionTypingServices.class);
        generator.addField(NeedSyntheticCallResolverExtension.class);
        generator.addField(false, PlatformToKotlinClassMap.class, null, new GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"));

        // Parameters
        generator.addPublicParameter(Project.class);
        generator.addParameter(ModuleDescriptor.class);

        generator.generate("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForMacros", GenerateInjectors.class);
    }

    private static void generateTestInjector() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();

        // Fields
        generator.addPublicField(DescriptorResolver.class);
        generator.addPublicField(ExpressionTypingServices.class);
        generator.addPublicField(TypeResolver.class);
        generator.addPublicField(CallResolver.class);
        generator.addField(NeedSyntheticCallResolverExtension.class);
        generator.addField(true, KotlinBuiltIns.class, null, new GivenExpression("KotlinBuiltIns.getInstance()"));
        generator.addField(false, PlatformToKotlinClassMap.class, null, new GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"));

        // Parameters
        generator.addPublicParameter(Project.class);
        generator.addParameter(ModuleDescriptor.class);

        generator.generate("compiler/tests", "org.jetbrains.jet.di", "InjectorForTests", GenerateInjectors.class);
    }

    private static void generateInjectorForJvmCodegen() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();

        // Parameters
        generator.addPublicParameter(JetTypeMapper.class);
        generator.addPublicParameter(GenerationState.class);
        generator.addParameter(ClassBuilderFactory.class);
        generator.addPublicParameter(Project.class);

        // Fields
        generator.addField(false, BindingTrace.class, "bindingTrace",
                           new GivenExpression("jetTypeMapper.getBindingTrace()"));
        generator.addField(false, BindingContext.class, "bindingContext",
                           new GivenExpression("bindingTrace.getBindingContext()"));
        generator.addField(false, ClassBuilderMode.class, "classBuilderMode",
                           new GivenExpression("classBuilderFactory.getClassBuilderMode()"));
        generator.addPublicField(ScriptCodegen.class);
        generator.addField(true, IntrinsicMethods.class, "intrinsics", null);
        generator.addPublicField(ClassFileFactory.class);

        generator.generate("compiler/backend/src", "org.jetbrains.jet.di", "InjectorForJvmCodegen", GenerateInjectors.class);
    }

    private static void generateInjectorForBodyResolve() throws IOException {
        DependencyInjectorGenerator generator = new DependencyInjectorGenerator();
        // Fields
        generator.addPublicField(BodyResolver.class);
        generator.addField(NeedSyntheticCallResolverExtension.class);
        generator.addField(false, PlatformToKotlinClassMap.class, null, new GivenExpression("moduleDescriptor.getPlatformToKotlinClassMap()"));

        // Parameters
        generator.addPublicParameter(Project.class);
        generator.addPublicParameter(TopDownAnalysisParameters.class);
        generator.addPublicParameter(BindingTrace.class);
        generator.addPublicParameter(BodiesResolveContext.class);
        generator.addParameter(ModuleDescriptor.class);
        generator.generate("compiler/frontend/src", "org.jetbrains.jet.di", "InjectorForBodyResolve", GenerateInjectors.class);
    }
}
