package com.champgm.intellij.plugin.preconditions;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

public class GeneratePreconditionsChecks extends AnAction {
    @Override
    public void actionPerformed(@NotNull final AnActionEvent actionEvent) {
        // Get the method in which the action occurred
        final PsiMethod psiMethod = getPsiMethodFromEvent(actionEvent);
        // And pass it into the generation util method
        createAndExecuteAction(psiMethod, actionEvent);
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        // This method is a check to see if this action is applicable at a certain point
        final PsiMethod psiMethodFromEvent = getPsiMethodFromEvent(event);

        // the getPsiMethodFromEvent will return null if something goes wrong, for example, if it is called with an
        // event that occurred outside of a method. Setting this as enabled or disabled will determine whether the
        // option to generate preconditions is given or not
        event.getPresentation().setEnabled(psiMethodFromEvent != null);
    }

    /**
     * Util method to create and execute a WriteCommandAction
     */
    private void createAndExecuteAction(@NotNull final PsiMethod psiMethod, final AnActionEvent actionEvent) {
        new WriteCommandAction.Simple(psiMethod.getProject(), psiMethod.getContainingFile()) {
            @Override
            protected void run() throws Throwable {
                generateAndAttachPreconditions(psiMethod, actionEvent);
            }
        }.execute();
    }

    private void generateAndAttachPreconditions(final PsiMethod psiMethod, final AnActionEvent actionEvent) {
        // This will be used to build individual statements from strings
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiMethod.getProject());

        // We'll need this to add each built statement to the body
        final PsiCodeBlock methodBody = psiMethod.getBody();
        if (methodBody != null) {

            // This is the "anchor" element, which we'll use to place each new statement
            final PsiJavaToken anchor = methodBody.getLBrace();

            for (final PsiParameter parameter : psiMethod.getParameterList().getParameters()) {

                // Extract the type and name of each parameter
                final PsiTypeElement typeElement = parameter.getTypeElement();
                if (typeElement != null) {
                    final String typeString = typeElement.toString();

                    // This will come out looking something like "PsiTypeElement:String", so grab the last part
                    final String type = typeString.substring(typeString.indexOf(":") + 1, typeString.length());
                    final String parameterName = parameter.getName();

                    // If it's a primitive type, don't add a preconditions check
                    if (!"byte".equals(type) &&
                            !"short".equals(type) &&
                            !"int".equals(type) &&
                            !"long".equals(type) &&
                            !"float".equals(type) &&
                            !"double".equals(type) &&
                            !"boolean".equals(type) &&
                            !"char".equals(type)) {
                        // Otherwise, start building the statement
                        final StringBuilder stringBuilder = new StringBuilder("com.google.common.base.Preconditions.check");

                        // If it's a string, we want to use StringUtils to check for blank or null strings
                        if ("String".equals(type)) {
                            stringBuilder.append("Argument(!org.apache.commons.lang.StringUtils.isBlank(")
                                    .append(parameterName)
                                    .append(", \"")
                                    .append(parameterName)
                                    .append(" may not be null or empty.\"));");
                        } else {
                            // Otherwise just check if it's null
                            stringBuilder.append("NotNull(")
                                    .append(parameterName)
                                    .append(", \"")
                                    .append(parameterName)
                                    .append(" may not be null.\");");
                        }

                        // build each new statement from a string
                        final PsiStatement statementFromText = elementFactory.createStatementFromText(stringBuilder.toString(), psiMethod);

                        // and add it after the left brace
                        methodBody.addAfter(statementFromText, anchor);
                    }
                }

            }
            createImports(actionEvent);
        }
    }

    /**
     * It turns out that creating proper/acceptable imports is a ridiculously complicated process with the given tools.
     * I found this suggested workaround in the IntelliJ forums
     */
    private void createImports(final AnActionEvent actionEvent) {
        final Project currentProject = getEventProject(actionEvent);
        final PsiFile currentFile = actionEvent.getData(LangDataKeys.PSI_FILE);
        if (currentProject != null && currentFile != null) {
            // Get an instance of this project's coding-style manager
            JavaCodeStyleManager.getInstance(currentProject)
                    // Tell it to shorten all class references accordingly
                    .shortenClassReferences(currentFile);
        }
    }

    /**
     * An action event contains a bunch of information about what has just taken place to trigger it and the code in
     * which it occurred. This method serves to both retrieve the method that the caret is currently placed in, and
     * also to provide some checks to make sure that this preconditions generation process is actually valid where the
     * carat is placed. Any failed checks should immediately return null, and the update() method that calls this will
     * disable this generation action
     */
    private PsiMethod getPsiMethodFromEvent(final AnActionEvent actionEvent) {
        // Grab a link to the file that was being edited
        final PsiFile file = actionEvent.getData(LangDataKeys.PSI_FILE);

        // And the editor being used
        final Editor editor = actionEvent.getData(PlatformDataKeys.EDITOR);
        if (file == null || editor == null) {
            actionEvent.getPresentation().setEnabled(false);
            return null;
        }

        // Find where the caret was when the action was triggered
        final int caretOffset = editor.getCaretModel().getOffset();

        // and the element that was at that position
        final PsiElement element = file.findElementAt(caretOffset);

        // fetch the method at that position
        final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

        // Make sure this method actually has parameters in it
        if (method != null) {
            if (method.getParameterList().getParameters().length > 0) {
                return method;
            }
        }

        // if method is null or if the method has no parameters, just return null.
        return null;
    }
}
