// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.augment;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLightField extends LightElement implements PsiField, PsiVariableEx, NavigationItem {

  /**
   * Possible modifiers for the generated fields. R classes for non-namespaced apps use final fields, all other R classes don't.
   */
  public enum FieldModifier {
    FINAL,
    NON_FINAL,
  }

  private final PsiClass myContext;
  private final PsiType myType;
  private final Object myConstantValue;

  private volatile PsiExpression myInitializer;
  private volatile String myName;
  private volatile LightModifierList myModifierList;

  AndroidLightField(@NotNull String name,
                           @NotNull PsiClass context,
                           @NotNull PsiType type,
                           @NotNull FieldModifier fieldModifier,
                           @Nullable Object constantValue) {
    super(context.getManager(), JavaLanguage.INSTANCE);
    myName = name;
    myType = type;
    myContext = context;
    myConstantValue = constantValue;

    final List<String> modifiers = new ArrayList<>();
    modifiers.add(PsiModifier.PUBLIC);
    modifiers.add(PsiModifier.STATIC);

    if (fieldModifier == FieldModifier.FINAL) {
      modifiers.add(PsiModifier.FINAL);
    }
    myModifierList = new LightModifierList(getManager(), getLanguage(), ArrayUtilRt.toStringArray(modifiers));
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @Override
  public PsiElement getParent() {
    return myContext;
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myContext.getContainingFile();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    myName = name;
    return this;
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    return computeConstantValue();
  }

  @Override
  public Object computeConstantValue() {
    return myConstantValue;
  }

  @Override
  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    myInitializer = initializer;
  }

  @Override
  public PsiExpression getInitializer() {
    return myInitializer;
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public PsiClass getContainingClass() {
    return myContext;
  }

  @Override
  public String toString() {
    return "AndroidLightField:" + getName();
  }

  @NotNull
  @Override
  public PsiType getType() {
    return myType;
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @NotNull
  @Override
  public PsiIdentifier getNameIdentifier() {
    return new LightIdentifier(getManager(), myName);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(this, PlatformIcons.FIELD_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }
}
