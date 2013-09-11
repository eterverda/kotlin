package org.jetbrains.jet.plugin.hierarchy.calls;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ConstructorDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.plugin.project.AnalyzerFacadeWithCache;
import org.jetbrains.jet.renderer.DescriptorRenderer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class KotlinCallHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable {
    private int usageCount = 1;
    private final List<PsiReference> references = new ArrayList<PsiReference>();
    private final boolean navigateToReference;

    public KotlinCallHierarchyNodeDescriptor(@NotNull Project project,
            HierarchyNodeDescriptor parentDescriptor,
            @NotNull PsiElement element,
            boolean isBase,
            boolean navigateToReference) {
        super(project, parentDescriptor, element, isBase);
        this.navigateToReference = navigateToReference;
    }

    public final void incrementUsageCount(){
        usageCount++;
    }

    public void addReference(PsiReference reference) {
        references.add(reference);
    }

    public boolean hasReference(PsiReference reference) {
        return references.contains(reference);
    }

    public final PsiElement getTargetElement(){
        return myElement;
    }

    @Override
    public final boolean isValid(){
        return myElement != null && myElement.isValid();
    }

    @Override
    public final boolean update(){
        CompositeAppearance oldText = myHighlightedText;
        Icon oldIcon = getIcon();

        int flags = Iconable.ICON_FLAG_VISIBILITY;
        if (isMarkReadOnly()) {
            flags |= Iconable.ICON_FLAG_READ_STATUS;
        }

        boolean changes = super.update();

        PsiElement targetElement = getTargetElement();
        String elementText = renderElement(targetElement);

        if (elementText == null) {
            String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
            if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
                myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
            }
            return true;
        }

        Icon newIcon = targetElement.getIcon(flags);
        if (changes && myIsBase) {
            LayeredIcon icon = new LayeredIcon(2);
            icon.setIcon(newIcon, 0);
            icon.setIcon(AllIcons.Hierarchy.Base, 1, -AllIcons.Hierarchy.Base.getIconWidth() / 2, 0);
            newIcon = icon;
        }
        setIcon(newIcon);

        myHighlightedText = new CompositeAppearance();
        TextAttributes mainTextAttributes = null;
        if (myColor != null) {
            mainTextAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
        }

        String packageName = null;
        String prefix = null;
        if (targetElement instanceof JetElement) {
            packageName = JetPsiUtil.getPackageName((JetElement) targetElement);
        }
        else {
            PsiClass enclosingClass = PsiTreeUtil.getParentOfType(targetElement, PsiClass.class, false);
            if (enclosingClass != null) {
                packageName = JavaHierarchyUtil.getPackageName(enclosingClass);
            }
            prefix = "Java   ";
        }

        if (prefix != null) {
            myHighlightedText.getEnding().addText(prefix, HierarchyNodeDescriptor.getPackageNameAttributes());
        }
        myHighlightedText.getEnding().addText(elementText, mainTextAttributes);

        if (usageCount > 1) {
            myHighlightedText.getEnding().addText(
                    IdeBundle.message("node.call.hierarchy.N.usages", usageCount),
                    HierarchyNodeDescriptor.getUsageCountPrefixAttributes()
            );
        }

        if (packageName != null) {
            myHighlightedText.getEnding().addText("  (" + packageName + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
        }

        myName = myHighlightedText.getText();

        if (!(Comparing.equal(myHighlightedText, oldText) && Comparing.equal(getIcon(), oldIcon))) {
            changes = true;
        }
        return changes;
    }

    @Nullable
    private static String renderElement(@Nullable PsiElement element) {
        String elementText;
        String containerText = null;

        if (element instanceof JetNamedDeclaration) {
            DescriptorRenderer renderer = DescriptorRenderer.SOURCE_CODE_SHORT_NAMES_IN_TYPES;

            BindingContext bindingContext =
                    AnalyzerFacadeWithCache.analyzeFileWithCache((JetFile) element.getContainingFile()).getBindingContext();

            DeclarationDescriptor descriptor = bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, element);
            if (descriptor == null) return null;

            if (element instanceof JetObjectDeclaration) {
                elementText = renderer.render(descriptor);
            }
            else if (element instanceof JetClass) {
                ClassDescriptor classDescriptor = (ClassDescriptor) descriptor;

                ConstructorDescriptor constructorDescriptor = classDescriptor.getUnsubstitutedPrimaryConstructor();
                if (constructorDescriptor == null) return null;

                elementText = renderer.render(constructorDescriptor);
            }
            else if (element instanceof JetNamedFunction || element instanceof JetProperty) {
                elementText = renderer.render(descriptor);
            }
            else return null;

            DeclarationDescriptor containerDescriptor = descriptor.getContainingDeclaration();
            if (containerDescriptor != null) {
                containerText = containerDescriptor.getName().asString();
            }
        }
        else if (element instanceof PsiMember) {
            PsiMember member = (PsiMember) element;

            if (member instanceof PsiMethod) {
                elementText = PsiFormatUtil.formatMethod(
                        (PsiMethod) member,
                        PsiSubstitutor.EMPTY,
                        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                        PsiFormatUtilBase.SHOW_TYPE
                );
            } else if (member instanceof PsiClass) {
                elementText = ClassPresentationUtil.getNameForClass((PsiClass) member, false);
            } else return null;

            PsiClass containingClass = member.getContainingClass();
            if (containingClass != null) {
                containerText = ClassPresentationUtil.getNameForClass(containingClass, false);
            }
        }
        else return null;

        return containerText == null ? elementText : elementText + " in " + containerText;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (!navigateToReference) {
            if (myElement instanceof Navigatable && ((Navigatable)myElement).canNavigate()) {
                ((Navigatable)myElement).navigate(requestFocus);
            }
            return;
        }

        PsiReference firstReference = references.get(0);
        PsiElement element = firstReference.getElement();
        if (element == null) return;
        PsiElement callElement = element.getParent();
        if (callElement instanceof Navigatable && ((Navigatable)callElement).canNavigate()) {
            ((Navigatable)callElement).navigate(requestFocus);
        } else {
            PsiFile psiFile = callElement.getContainingFile();
            if (psiFile == null || psiFile.getVirtualFile() == null) return;
            FileEditorManager.getInstance(myProject).openFile(psiFile.getVirtualFile(), requestFocus);
        }

        Editor editor = PsiUtilBase.findEditor(callElement);

        if (editor != null) {
            HighlightManager highlightManager = HighlightManager.getInstance(myProject);
            EditorColorsManager colorManager = EditorColorsManager.getInstance();
            TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
            List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
            for (PsiReference psiReference : references) {
                PsiElement eachElement = psiReference.getElement();
                if (eachElement != null) {
                    PsiElement eachMethodCall = eachElement.getParent();
                    if (eachMethodCall != null) {
                        TextRange textRange = eachMethodCall.getTextRange();
                        highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, false, highlighters);
                    }
                }
            }
        }
    }

    @Override
    public boolean canNavigate() {
        if (!navigateToReference) {
            return myElement instanceof Navigatable && ((Navigatable) myElement).canNavigate();
        }
        if (references.isEmpty()) return false;
        PsiReference firstReference = references.get(0);
        PsiElement callElement = firstReference.getElement().getParent();
        if (callElement == null || !callElement.isValid()) return false;
        if (!(callElement instanceof Navigatable) || !((Navigatable)callElement).canNavigate()) {
            PsiFile psiFile = callElement.getContainingFile();
            if (psiFile == null) return false;
        }
        return true;
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }
}
