package org.jetbrains.jet.plugin.hierarchy.calls;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.asJava.LightClassUtil;
import org.jetbrains.jet.lang.psi.JetClassOrObject;
import org.jetbrains.jet.lang.psi.JetNamedFunction;
import org.jetbrains.jet.lang.psi.JetProperty;

public abstract class KotlinCallTreeStructure extends HierarchyTreeStructure {
    public KotlinCallTreeStructure(@NotNull Project project, HierarchyNodeDescriptor baseDescriptor) {
        super(project, baseDescriptor);
    }

    @Nullable
    protected PsiClass getBasePsiClass() {
        PsiElement baseElement = ((KotlinCallHierarchyNodeDescriptor) getBaseDescriptor()).getTargetElement();

        PsiClass baseClass = null;
        if (baseElement instanceof JetNamedFunction) {
            PsiMethod baseLightMethod = LightClassUtil.getLightClassMethod((JetNamedFunction) baseElement);
            if (baseLightMethod != null) {
                baseClass = baseLightMethod.getContainingClass();
            }
        }
        else if (baseElement instanceof JetProperty) {
            LightClassUtil.PropertyAccessorsPsiMethods propertyMethods =
                    LightClassUtil.getLightClassPropertyMethods((JetProperty) baseElement);
            if (propertyMethods.getGetter() != null) {
                baseClass = propertyMethods.getGetter().getContainingClass();
            }
            else if (propertyMethods.getSetter() != null) {
                baseClass = propertyMethods.getSetter().getContainingClass();
            }
        }
        else if (baseElement instanceof JetClassOrObject) {
            baseClass = LightClassUtil.getPsiClass((JetClassOrObject) baseElement);
        }
        return baseClass;
    }

    @Override
    public boolean isAlwaysShowPlus() {
        return true;
    }
}
