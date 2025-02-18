/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.groovy;

import static com.intellij.openapi.util.text.StringUtil.isQuotedString;
import static com.intellij.openapi.util.text.StringUtil.unquoteString;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOLON;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMMA;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil.addQuotes;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil.escapeStringCharacters;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil.removeQuotes;

import com.android.tools.idea.gradle.dsl.api.ext.RawText;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

public final class GroovyDslUtil {
  @Nullable
  static GroovyPsiElement ensureGroovyPsi(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof GroovyPsiElement) {
      return (GroovyPsiElement)element;
    }
    throw new IllegalArgumentException("Wrong PsiElement type for writer! Must be of type GoovyPsiElement");
  }

  static void addConfigBlock(@NotNull GradleDslSettableExpression expression) {
    PsiElement unsavedConfigBlock = expression.getUnsavedConfigBlock();
    if (unsavedConfigBlock == null) {
      return;
    }

    GroovyPsiElement psiElement = ensureGroovyPsi(expression.getPsiElement());
    if (psiElement == null) {
      return;
    }

    GroovyPsiElementFactory factory = getPsiElementFactory(expression);
    if (factory == null) {
      return;
    }

    // For now, this is only reachable for newly added dependencies, which means psiElement is an application statement with three children:
    // the configuration name, whitespace, dependency in compact notation. Let's add some more: comma, whitespace and finally the config
    // block.
    GrApplicationStatement methodCallStatement = (GrApplicationStatement)factory.createStatementFromText("foo 1, 2");
    PsiElement comma = methodCallStatement.getArgumentList().getFirstChild().getNextSibling();

    psiElement.addAfter(comma, psiElement.getLastChild());
    psiElement.addAfter(factory.createWhiteSpace(), psiElement.getLastChild());
    psiElement.addAfter(unsavedConfigBlock, psiElement.getLastChild());
    expression.setUnsavedConfigBlock(null);
  }

  @Nullable
  static GrClosableBlock getClosableBlock(@NotNull PsiElement element) {
    if (!(element instanceof GrMethodCallExpression)) {
      return null;
    }

    GrClosableBlock[] closureArguments = ((GrMethodCallExpression)element).getClosureArguments();
    if (closureArguments.length > 0) {
      return closureArguments[0];
    }

    return null;
  }

  static GroovyPsiElementFactory getPsiElementFactory(@NotNull GradleDslElement element) {
    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement == null) {
      return null;
    }

    Project project = psiElement.getProject();
    return GroovyPsiElementFactory.getInstance(project);
  }

  static boolean isNewEmptyBlockElement(@NotNull GradleDslElement element) {
    if (element.getPsiElement() != null) {
      return false;
    }

    if (!element.isBlockElement() || !element.isInsignificantIfEmpty()) {
      return false;
    }

    Collection<GradleDslElement> children = element.getChildren();
    if (children.isEmpty()) {
      return true;
    }

    for (GradleDslElement child : children) {
      if (!isNewEmptyBlockElement(child)) {
        return false;
      }
    }

    return true;
  }

  static void maybeDeleteIfEmpty(@Nullable PsiElement element, @NotNull GradleDslElement dslElement) {
    GradleDslElement parentDslElement = dslElement.getParent();
    if ((parentDslElement instanceof GradleDslExpressionList && !((GradleDslExpressionList)parentDslElement).shouldBeDeleted()) ||
        (parentDslElement instanceof GradleDslExpressionMap && !((GradleDslExpressionMap)parentDslElement).shouldBeDeleted()) &&
        parentDslElement.getPsiElement() == element) {
      // Don't delete parent if empty.
      return;
    }
    deleteIfEmpty(element, dslElement);
  }

  private static void deleteIfEmpty(@Nullable PsiElement element, @NotNull GradleDslElement containingDslElement) {
    if (element == null) {
      return;
    }

    PsiElement parent = element.getParent();
    GradleDslElement dslParent = getNextValidParent(containingDslElement);

    if (!element.isValid()) {
      // Skip deleting
    }
    else if (element instanceof GrAssignmentExpression) {
      if (((GrAssignmentExpression)element).getRValue() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrApplicationStatement) {
      if (((GrApplicationStatement)element).getArgumentList() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrClosableBlock) {
      if (dslParent == null || dslParent.isInsignificantIfEmpty()) {
        final Boolean[] isEmpty = new Boolean[]{true};
        ((GrClosableBlock)element).acceptChildren(new GroovyElementVisitor() {
          @Override
          public void visitElement(@NotNull GroovyPsiElement child) {
            if (child instanceof GrParameterList) {
              if (((GrParameterList)child).getParameters().length == 0) {
                return; // Ignore the empty parameter list.
              }
            }
            isEmpty[0] = false;
          }
        });
        if (isEmpty[0]) {
          element.delete();
        }
      }
    }
    else if (element instanceof GrMethodCallExpression) {
      GrMethodCallExpression call = ((GrMethodCallExpression)element);
      GrArgumentList argumentList = null;
      try {
        for (PsiElement curr = call.getFirstChild(); curr != null; curr = curr.getNextSibling()) {
          if (curr instanceof GrArgumentList) {
            argumentList = (GrArgumentList)curr;
            break;
          }
        }
      }
      catch (AssertionError e) {
        // We will get this exception if the argument list is already deleted.
        argumentList = null;
      }
      GrClosableBlock[] closureArguments = call.getClosureArguments();
      if ((argumentList == null || argumentList.getAllArguments().length == 0)
          && closureArguments.length == 0) {
        element.delete();
      }
    }
    else if (element instanceof GrCommandArgumentList) {
      GrCommandArgumentList commandArgumentList = (GrCommandArgumentList)element;
      if (commandArgumentList.getAllArguments().length == 0) {
        commandArgumentList.delete();
      }
    }
    else if (element instanceof GrNamedArgument) {
      GrNamedArgument namedArgument = (GrNamedArgument)element;
      if (namedArgument.getExpression() == null) {
        namedArgument.delete();
      }
    }
    else if (element instanceof GrVariableDeclaration) {
      GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)element;
      for (GrVariable grVariable : variableDeclaration.getVariables()) {
        if (grVariable.getInitializerGroovy() == null) {
          grVariable.delete();
        }
      }
      // If we have no more variables, delete the declaration.
      if (variableDeclaration.getVariables().length == 0) {
        variableDeclaration.delete();
      }
    }
    else if (element instanceof GrVariable) {
      GrVariable variable = (GrVariable)element;
      if (variable.getInitializerGroovy() == null) {
        variable.delete();
      }
    }
    else if (element instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)element;
      if (listOrMap.isMap() && listOrMap.getNamedArguments().length == 0) {
        listOrMap.delete();
      }
      else if (!listOrMap.isMap() && listOrMap.getInitializers().length == 0) {
        listOrMap.delete();
      }
    }

    if (!element.isValid()) {
      // Give the parent a chance to adapt to the missing child.
      handleElementRemoved(parent, element);
      // If this element is deleted, also delete the parent if it is empty.
      if (dslParent != null && dslParent.isInsignificantIfEmpty()) {
        maybeDeleteIfEmpty(parent, element == dslParent.getPsiElement() ? dslParent : containingDslElement);
      }
    }
  }

  @Nullable
  static GradleDslElement getNextValidParent(@NotNull GradleDslElement element) {
    PsiElement psi = element.getPsiElement();
    while (element != null && (psi == null || !psi.isValid())) {
      element = element.getParent();
      if (element != null) {
        psi = element.getPsiElement();
      }
    }

    return element;
  }

  static void removePsiIfInvalid(@Nullable GradleDslElement element) {
    if (element == null) {
      return;
    }

    if (element.getPsiElement() != null && !element.getPsiElement().isValid()) {
      element.setPsiElement(null);
    }

    if (element.getParent() != null) {
      removePsiIfInvalid(element.getParent());
    }
  }

  /**
   * This method is used to edit the PsiTree once an element has been deleted.
   * <p>
   * It currently only looks at GrListOrMap to insert a ":" into a map. This is needed because once we delete
   * the final element in a map we are left with [], which is a list.
   */
  static void handleElementRemoved(@Nullable PsiElement psiElement, @Nullable PsiElement removed) {
    if (psiElement == null) {
      return;
    }

    if (psiElement instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)psiElement;
      // Make sure it was being used as a map
      if (removed instanceof GrNamedArgument) {
        if (listOrMap.isEmpty()) {
          final ASTNode node = listOrMap.getNode();
          node.addLeaf(mCOLON, ":", listOrMap.getRBrack().getNode());
        }
      }
    }
  }

  @Nullable
  static GrExpression extractUnsavedExpression(@NotNull GradleDslSettableExpression literal) {
    GroovyPsiElement newElement = ensureGroovyPsi(literal.getUnsavedValue());
    if (!(newElement instanceof GrExpression)) {
      return null;
    }

    return (GrExpression)newElement;
  }

  private static String escapeString(@NotNull String str, boolean forGString) {
    StringBuilder sb = new StringBuilder();
    escapeStringCharacters(str.length(), str, forGString ? "\"" : "'", true, true, sb);
    return sb.toString();
  }

  /**
   * Creates a literal from a context and value.
   *
   * @param context      context used to create GrPsiElementFactory
   * @param unsavedValue the value for the new expression
   * @return created PsiElement
   * @throws IncorrectOperationException if creation of the expression fails
   */
  @Nullable
  static PsiElement createLiteral(@NotNull GradleDslElement context, @NotNull Object unsavedValue) throws IncorrectOperationException {
    CharSequence unsavedValueText = null;
    if (unsavedValue instanceof String) {
      String stringValue = (String)unsavedValue;
      if (isQuotedString(stringValue)) {
        // We need to escape the string without the quotes and then add them back.
        String unquotedString = removeQuotes(stringValue);
        unsavedValueText = addQuotes(escapeString(unquotedString, true), true);
      }
      else {
        unsavedValueText = addQuotes(escapeString((String)unsavedValue, false), false);
      }
    }
    else if (unsavedValue instanceof Integer || unsavedValue instanceof Boolean) {
      unsavedValueText = unsavedValue.toString();
    }
    else if (unsavedValue instanceof RawText) {
      unsavedValueText = ((RawText)unsavedValue).getText();
    }

    if (unsavedValueText == null) {
      return null;
    }

    GroovyPsiElementFactory factory = getPsiElementFactory(context);
    if (factory == null) {
      return null;
    }

    return factory.createExpressionFromText(unsavedValueText);
  }

  /**
   * Creates a literal expression map enclosed with brackets "[]" from the given {@link GradleDslExpressionMap}.
   */
  static PsiElement createDerivedMap(@NotNull GradleDslExpressionMap expressionMap) {
    PsiElement parentPsiElement = getParentPsi(expressionMap);
    if (parentPsiElement == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    GrExpression emptyMap = factory.createExpressionFromText("[:]");
    GrNamedArgument namedArgument = factory.createNamedArgument(expressionMap.getName(), emptyMap);
    PsiElement addedElement = addToMap((GrListOrMap)parentPsiElement, namedArgument);
    assert addedElement instanceof GrNamedArgument;

    PsiElement added = ((GrNamedArgument)addedElement).getExpression();
    expressionMap.setPsiElement(added);
    return added;
  }

  /**
   * This method is used in order to add elements to the back of a map,
   * it is derived from {@link ASTDelegatePsiElement#addInternal(ASTNode, ASTNode, ASTNode, Boolean)}.
   */
  private static PsiElement realAddBefore(@NotNull GrListOrMap element, @NotNull PsiElement newElement, @NotNull PsiElement anchor) {
    CheckUtil.checkWritable(element);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    ASTNode anchorNode = getAnchorNode(element, anchor.getNode(), true);
    ASTNode newNode = CodeEditUtil.addChildren(element.getNode(), elementCopy, elementCopy, anchorNode);
    if (newNode == null) {
      throw new IncorrectOperationException("Element cannot be added");
    }
    if (newNode instanceof TreeElement) {
      return ChangeUtil.decodeInformation((TreeElement)newNode).getPsi();
    }
    return newNode.getPsi();
  }

  /**
   * This method has been taken from {@link ASTDelegatePsiElement} in order to implement a correct version of
   * {@link #realAddBefore}.
   */
  private static ASTNode getAnchorNode(@NotNull PsiElement element, final ASTNode anchor, final Boolean before) {
    ASTNode anchorBefore;
    if (anchor != null) {
      anchorBefore = before.booleanValue() ? anchor : anchor.getTreeNext();
    }
    else {
      if (before != null && !before.booleanValue()) {
        anchorBefore = element.getNode().getFirstChildNode();
      }
      else {
        anchorBefore = null;
      }
    }
    return anchorBefore;
  }

  static PsiElement addToMap(@NotNull GrListOrMap map, @NotNull GrNamedArgument newValue) {
    final ASTNode astNode = map.getNode();
    if (map.getNamedArguments().length != 0) {
      astNode.addLeaf(mCOMMA, ",", map.getRBrack().getNode());
    }
    else {
      // Empty maps are defined by [:], we need to delete the colon before adding the first element.
      while (map.getLBrack().getNextSibling() != map.getRBrack()) {
        map.getLBrack().getNextSibling().delete();
      }
    }

    return realAddBefore(map, newValue, map.getRBrack());
  }

  @Nullable
  static PsiElement processListElement(@NotNull GradleDslSettableExpression expression) {
    GradleDslElement parent = expression.getParent();
    if (parent == null) {
      return null;
    }

    PsiElement parentPsi = parent.create();
    if (parentPsi == null) {
      return null;
    }

    PsiElement newExpressionPsi = expression.getUnsavedValue();
    if (newExpressionPsi == null) {
      return null;
    }

    PsiElement added = createPsiElementInsideList(parent, expression, parentPsi, newExpressionPsi);
    expression.setPsiElement(added);
    expression.commit();
    return expression.getPsiElement();
  }

  @Nullable
  static PsiElement processMapElement(@NotNull GradleDslSettableExpression expression) {
    GradleDslElement parent = expression.getParent();
    assert parent != null;

    GroovyPsiElement parentPsiElement = ensureGroovyPsi(parent.create());
    if (parentPsiElement == null) {
      return null;
    }

    expression.setPsiElement(parentPsiElement);
    GrExpression newLiteral = extractUnsavedExpression(expression);
    if (newLiteral == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(newLiteral.getProject());
    GrNamedArgument namedArgument = factory.createNamedArgument(expression.getName(), newLiteral);
    PsiElement added;
    if (parentPsiElement instanceof GrArgumentList) {
      added = ((GrArgumentList)parentPsiElement).addNamedArgument(namedArgument);
    }
    else if (parentPsiElement instanceof GrListOrMap) {
      GrListOrMap grListOrMap = (GrListOrMap)parentPsiElement;
      added = addToMap(grListOrMap, namedArgument);
    }
    else {
      added = parentPsiElement.addBefore(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      GrExpression grExpression = getChildOfType(addedNameArgument, GrExpression.class);
      if (grExpression != null) {
        expression.setExpression(grExpression);
        expression.commit();
        expression.reset();
        return expression.getPsiElement();
      }
      else {
        return null;
      }
    }
    else {
      throw new IllegalStateException("Unexpected element type added to Mpa: " + added);
    }
  }

  static void applyDslLiteralOrReference(@NotNull GradleDslSettableExpression expression) {
    PsiElement psiElement = ensureGroovyPsi(expression.getPsiElement());
    if (psiElement == null) {
      return;
    }

    maybeUpdateName(expression);

    GrExpression newLiteral = extractUnsavedExpression(expression);
    if (newLiteral == null) {
      return;
    }
    PsiElement psiExpression = ensureGroovyPsi(expression.getExpression());
    if (psiExpression != null) {
      PsiElement replace = psiExpression.replace(newLiteral);
      if (replace instanceof GrLiteral || replace instanceof GrReferenceExpression || replace instanceof GrIndexProperty) {
        expression.setExpression(replace);
      }
    }
    else {
      // This element has just been created and will currently look like "propertyName =" or "propertyName ". Here we add the value.
      PsiElement added = psiElement.addAfter(newLiteral, psiElement.getLastChild());
      expression.setExpression(added);

      if (expression.getUnsavedConfigBlock() != null) {
        addConfigBlock(expression);
      }
    }

    expression.reset();
    expression.commit();
  }

  @Nullable
  static PsiElement createNamedArgumentList(@NotNull GradleDslExpressionList expressionList) {
    GradleDslElement parent = expressionList.getParent();
    assert parent instanceof GradleDslExpressionMap;

    PsiElement parentPsiElement = parent.create();
    if (parentPsiElement == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    GrExpression expressionFromText = factory.createExpressionFromText("[]");
    GrNamedArgument namedArgument = factory.createNamedArgument(expressionList.getName(), expressionFromText);
    PsiElement added;
    if (parentPsiElement instanceof GrArgumentList) {
      GrArgumentList argList = (GrArgumentList)parentPsiElement;
      // This call can return a dummy PsiElement. We can't use its return value.
      argList.addNamedArgument(namedArgument);

      GrNamedArgument[] args = argList.getNamedArguments();
      added = args[args.length - 1];
    }
    else if (parentPsiElement instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)parentPsiElement;
      // For list and maps we need to add the element delimiter "," after the added element if there is more than one.
      if (!listOrMap.isEmpty()) {
        final ASTNode node = listOrMap.getNode();
        node.addLeaf(mCOMMA, ",", listOrMap.getLBrack().getNextSibling().getNode());
      }
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    else {
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      expressionList.setPsiElement(addedNameArgument.getExpression());
      return expressionList.getPsiElement();
    }
    return null;
  }

  @Nullable
  static String getInjectionName(@NotNull GrStringInjection injection) {
    String variableName = null;

    GrClosableBlock closableBlock = injection.getClosableBlock();
    if (closableBlock != null) {
      String blockText = closableBlock.getText();
      variableName = blockText.substring(1, blockText.length() - 1);
    }
    else {
      GrExpression expression = injection.getExpression();
      if (expression != null) {
        variableName = expression.getText();
      }
    }

    return variableName;
  }

  @NotNull
  static String ensureUnquotedText(@NotNull String str) {
    if (isQuotedString(str)) {
      str = unquoteString(str);
    }
    return str;
  }

  @Nullable
  static PsiElement getParentPsi(@NotNull GradleDslElement element) {
    GradleDslElement parent = element.getParent();
    if (parent == null) {
      return null;
    }

    GroovyPsiElement parentPsiElement = ensureGroovyPsi(parent.create());
    if (parentPsiElement == null) {
      return null;
    }
    return parentPsiElement;
  }

  static String maybeTrimForParent(@NotNull GradleNameElement name, @Nullable GradleDslElement parent) {
    if (parent == null) {
      return name.fullName();
    }

    List<String> parts = new ArrayList<>(name.fullNameParts());
    if (parts.isEmpty()) {
      return name.fullName();
    }
    String lastNamePart = parts.remove(parts.size() - 1);
    List<String> parentParts = Splitter.on(".").splitToList(parent.getQualifiedName());
    int i = 0;
    while (i < parentParts.size() && !parts.isEmpty() && parentParts.get(i).equals(parts.get(0))) {
      parts.remove(0);
      i++;
    }
    parts.add(lastNamePart);
    return GradleNameElement.createNameFromParts(parts);
  }

  /**
   * This method is required to work out whether a GradleDslReference or GradleDslLiteral is an internal value in a map.
   * This allows us to add the PsiElement into the correct position, note: due to the PsiElements Api we have to add the
   * ASTNodes directly in {@link #emplaceElementIntoList(PsiElement, PsiElement, PsiElement)}. This method checks the specific
   * conditions where we need to add an element to the inside of a literal list. The reason we have to do it this way
   * is that when we are applying a GradleDslReference or GradleDslLiteral we don't know whether (1) we are actually in a list and (2)
   * whether the list actually needs us to add a comma. Ideally we would have the apply/create/delete methods of GradleDslExpressionList
   * position the arguments. This is a workaround for now.
   * <p>
   * Note: In order to get the position of where to insert the item, we set the PsiElement of the literal/reference to be the previous
   * item in the list (this is done in GradleDslExpressionList) and then set it back once we have called apply.
   */
  static boolean shouldAddToListInternal(@NotNull GradleDslElement element) {
    GradleDslElement parent = element.getParent();
    if (!(parent instanceof GradleDslExpressionList)) {
      return false;
    }
    PsiElement parentPsi = parent.getPsiElement();
    return ((parentPsi instanceof GrListOrMap && ((GrListOrMap)parentPsi).getInitializers().length > 0) ||
            (parentPsi instanceof GrArgumentList && ((GrArgumentList)parentPsi).getAllArguments().length > 0));
  }

  static void emplaceElementIntoList(@NotNull PsiElement anchorBefore, @NotNull PsiElement list, @NotNull PsiElement newElement) {
    final ASTNode node = list.getNode();
    final ASTNode anchor = anchorBefore.getNode().getTreeNext();
    node.addChild(newElement.getNode(), anchor);
    node.addLeaf(mCOMMA, ",", newElement.getNode());
  }

  static PsiElement emplaceElementToFrontOfList(@NotNull PsiElement listElement, @NotNull PsiElement newElement) {
    assert listElement instanceof GrListOrMap || listElement instanceof GrArgumentList;
    final ASTNode node = listElement.getNode();
    if (listElement instanceof GrListOrMap) {
      GrListOrMap list = (GrListOrMap)listElement;
      final ASTNode anchor = list.getLBrack().getNode().getTreeNext();
      if (!list.isEmpty()) {
        node.addLeaf(mCOMMA, ",", anchor);
        node.addLeaf(TokenType.WHITE_SPACE, " ", anchor);
      }
      // We want to anchor this off the added mCOMMA node.
      node.addChild(newElement.getNode(), list.getLBrack().getNode().getTreeNext());
    }
    else if (((GrArgumentList)listElement).getLeftParen() != null) {
      GrArgumentList list = (GrArgumentList)listElement;
      PsiElement leftParen = list.getLeftParen();
      assert leftParen != null;
      final ASTNode anchor = list.getLeftParen().getNode().getTreeNext();
      if (list.getAllArguments().length != 0) {
        node.addLeaf(mCOMMA, ",", anchor);
        node.addLeaf(TokenType.WHITE_SPACE, " ", anchor);
      }
      node.addChild(newElement.getNode(), list.getLeftParen().getNode().getTreeNext());
    }
    else {
      ASTNode anchor = getFirstASTNode(listElement);
      if (anchor != null) {
        node.addLeaf(mCOMMA, ",", anchor);
        node.addLeaf(TokenType.WHITE_SPACE, " ", anchor);
      }
      // We want to anchor this off the added mCOMMA node
      node.addChild(newElement.getNode(), getFirstASTNode(listElement));
    }

    return newElement;
  }

  @Nullable
  static ASTNode getFirstASTNode(@NotNull PsiElement parent) {
    final PsiElement firstChild = parent.getFirstChild();
    if (firstChild == null) {
      return null;
    }
    return firstChild.getNode();
  }

  @NotNull
  static PsiElement createPsiElementInsideList(@NotNull GradleDslElement parentDslElement,
                                               @NotNull GradleDslElement dslElement,
                                               @NotNull PsiElement parentPsiElement,
                                               @NotNull PsiElement newElement) {
    PsiElement added;
    GradleDslElement anchor = parentDslElement.requestAnchor(dslElement);
    if (shouldAddToListInternal(dslElement) && anchor != null) {
      // Get the anchor
      PsiElement anchorPsi = anchor.getPsiElement();
      assert anchorPsi != null;

      emplaceElementIntoList(anchorPsi, parentPsiElement, newElement);
      added = newElement;
    }
    else {
      added = emplaceElementToFrontOfList(parentPsiElement, newElement);
    }

    return added;
  }

  @Nullable
  static PsiElement createNameElement(@NotNull GradleDslElement context, @NotNull String name) {
    GroovyPsiElementFactory factory = getPsiElementFactory(context);
    if (factory == null) {
      return null;
    }

    String str = name + " = 1";
    GrExpression expression = factory.createExpressionFromText(str);
    assert expression instanceof GrAssignmentExpression;
    return ((GrAssignmentExpression)expression).getLValue();
  }

  static void maybeUpdateName(@NotNull GradleDslElement element) {
    PsiElement oldName = element.getNameElement().getNamedPsiElement();
    String newName = element.getNameElement().getUnsavedName();
    PsiElement newElement;
    if (newName == null || oldName == null) {
      return;
    }
    if (oldName instanceof PsiNamedElement) {
      PsiNamedElement namedElement = (PsiNamedElement)oldName;
      namedElement.setName(newName);
      newElement = namedElement;
    }
    else {
      PsiElement psiElement = createNameElement(element, newName);
      if (psiElement == null) {
        throw new IllegalStateException("Can't create new GrExpression for name element");
      }
      newElement = oldName.replace(psiElement);
    }
    element.getNameElement().commitNameChange(newElement);
  }

  /**
   * @param startElement starting element
   * @return the last none null psi element in the tree starting at node startElement.
   */
  @Nullable
  static PsiElement findLastPsiElementIn(@NotNull GradleDslElement startElement) {
    PsiElement psiElement = startElement.getPsiElement();
    if (psiElement != null) {
      return psiElement;
    }

    for (GradleDslElement element : Lists.reverse(new ArrayList<>(startElement.getChildren()))) {
      if (element != null) {
        PsiElement psi = findLastPsiElementIn(element);
        if (psi != null) {
          return psi;
        }
      }
    }
    return null;
  }

  @Nullable
  static PsiElement getPsiElementForAnchor(@NotNull PsiElement parent, @Nullable GradleDslElement dslAnchor) {
    PsiElement anchorAfter = dslAnchor == null ? null : findLastPsiElementIn(dslAnchor);
    if (anchorAfter == null && parent instanceof GrClosableBlock) {
      return adjustForCloseableBlock((GrClosableBlock)parent);
    }
    else {
      while (anchorAfter != null && !(anchorAfter instanceof PsiFile) && anchorAfter.getParent() != parent) {
        anchorAfter = anchorAfter.getParent();
      }
      return anchorAfter instanceof PsiFile
             ? (parent instanceof GrClosableBlock) ? adjustForCloseableBlock((GrClosableBlock)parent) : null
             : anchorAfter;
    }
  }

  private static PsiElement adjustForCloseableBlock(@NotNull GrClosableBlock block) {
    PsiElement element = block.getFirstChild();
    // Skip the first non-empty element, this is normally the '{' of a closable block.
    if (element != null) {
      element = element.getNextSibling();
    }

    // Find the last empty (no newlines or content) child after the initial element.
    while (element != null) {
      element = element.getNextSibling();
      if (element != null && (Strings.isNullOrEmpty(element.getText()) || element.getText().matches("[\\t ]+"))) {
        continue;
      }
      break;
    }

    return element == null ? null : element.getPrevSibling();
  }

  static boolean needToCreateParent(@NotNull GradleDslElement element) {
    GradleDslElement parent = element.getParent();
    return parent != null && parent.getPsiElement() == null;
  }

  static boolean hasNewLineBetween(@NotNull PsiElement start, @NotNull PsiElement end) {
    assert start.getParent() == end.getParent() && start.getStartOffsetInParent() <= end.getStartOffsetInParent();
    for (PsiElement element = start; element != end; element = element.getNextSibling()) {
      if (element.getNode().getElementType().equals(GroovyTokenTypes.mNLS)) {
        return true;
      }
    }
    return false;
  }

  static List<GradleReferenceInjection> findInjections(@NotNull GradleDslSimpleExpression context,
                                                       @NotNull PsiElement psiElement,
                                                       boolean includeUnresolved) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (psiElement instanceof GrReferenceExpression || psiElement instanceof GrIndexProperty) {
      String text = psiElement.getText();
      GradleDslElement element = context.resolveReference(text, true);
      return ImmutableList.of(new GradleReferenceInjection(context, element, psiElement, text));
    }

    if (!(psiElement instanceof GrString)) {
      return Collections.emptyList();
    }

    List<GradleReferenceInjection> injections = Lists.newArrayList();
    GrStringInjection[] grStringInjections = ((GrString)psiElement).getInjections();
    for (GrStringInjection injection : grStringInjections) {
      if (injection != null) {
        String name = getInjectionName(injection);
        if (name != null) {
          GradleDslElement referenceElement = context.resolveReference(name, true);
          if (includeUnresolved || referenceElement != null) {
            injections.add(new GradleReferenceInjection(context, referenceElement, injection, name));
          }
        }
      }
    }
    return injections;
  }

  static void createAndAddClosure(@NotNull GradleDslClosure closure, @NotNull GradleDslElement element) {
    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement == null) {
      return;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
    GrClosableBlock block = factory.createClosureFromText("{ }");
    psiElement.addAfter(factory.createWhiteSpace(), psiElement.getLastChild());
    PsiElement newElement = psiElement.addAfter(block, psiElement.getLastChild());
    closure.setPsiElement(newElement);
    closure.applyChanges();
    element.setParsedClosureElement(closure);
    element.setNewClosureElement(null);
  }

  static void deletePsiElement(@NotNull GradleDslElement context, @Nullable PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) {
      return;
    }

    PsiElement parent = psiElement.getParent();
    psiElement.delete();

    maybeDeleteIfEmpty(parent, context);

    // Now we have deleted all empty PsiElements in the Psi tree, we also need to make sure
    // to clear any invalid PsiElements in the GradleDslElement tree otherwise we will
    // be prevented from recreating these elements.
    removePsiIfInvalid(context);
  }
}
