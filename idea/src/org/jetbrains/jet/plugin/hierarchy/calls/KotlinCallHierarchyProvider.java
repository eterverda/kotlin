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

package org.jetbrains.jet.plugin.hierarchy.calls;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetNamedDeclaration;
import org.jetbrains.jet.lang.psi.JetNamedFunction;
import org.jetbrains.jet.lang.psi.JetProperty;

public class KotlinCallHierarchyProvider implements HierarchyProvider {
    @Override
    public PsiElement getTarget(@NotNull DataContext dataContext) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null) return null;

        PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        //noinspection unchecked
        return PsiTreeUtil.getNonStrictParentOfType(element, JetNamedFunction.class, JetClassOrObject.class, JetProperty.class);
    }

    @NotNull
    @Override
    public HierarchyBrowser createHierarchyBrowser(PsiElement target) {
        return new KotlinCallHierarchyBrowser(target.getProject(), (JetNamedDeclaration)target);
    }

    @Override
    public void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser) {
        ((KotlinCallHierarchyBrowser) hierarchyBrowser).changeView(CallHierarchyBrowserBase.CALLER_TYPE);
    }
}
