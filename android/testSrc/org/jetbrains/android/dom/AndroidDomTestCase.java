/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.spellchecker.quickfixes.SaveTo;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.AndroidDomInspection;
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.inspections.AndroidMissingOnClickHandlerInspection;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract class for creating unit tests for XML editor. Contains utility methods for performing things such as
 * <ul>
 * <li>Checking highlighting: {@link #doTestHighlighting()}</li>
 * <li>Checking results of code completion: {@link #doTestCompletionVariants(String, String...)}, {@link #toTestCompletion(String, String)}
 * and {@link #toTestFirstCompletion(String, String)}</li>
 * </ul>
 * Some of these methods use test name to choose a file from testData folder they're going to use, look out for
 * {@link #getTestName(String, boolean)} and similar methods to spot that.
 */
@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class AndroidDomTestCase extends AndroidTestCase {
  protected final String myTestFolder;

  protected AndroidDomTestCase(String testFolder) {
    myTestFolder = testFolder;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ensureWebserverAccess();
    copyRJavaToGeneratedSources();
    //noinspection unchecked
    myFixture.enableInspections(AndroidDomInspection.class,
                                AndroidUnknownAttributeInspection.class,
                                AndroidElementNotAllowedInspection.class);
  }

  private static void ensureWebserverAccess() {
    try {
      Class builtinWebServerAccess = Class.forName("com.intellij.util.BuiltinWebServerAccess");
      if (builtinWebServerAccess != null) {
        //noinspection unchecked
        Method ensureUserAuthenticationToken =
          builtinWebServerAccess.getMethod("ensureUserAuthenticationToken");
        if (ensureUserAuthenticationToken != null) {
          ensureUserAuthenticationToken.invoke(null);
        }
      }
    }
    catch (ClassNotFoundException |
      NoSuchMethodException |
      InvocationTargetException |
      IllegalAccessException ignore) {
      // that's ok, it indicates we're not running in Android Studio, but in IntelliJ ultimate.
    }
  }

  @Override
  protected final String getResDir() {
    return "dom/res";
  }

  protected final void doTestJavaCompletion(@NotNull String aPackage) throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String fileName = getTestName(false) + ".java";
    VirtualFile file = copyFileToProject(fileName, "src/" + aPackage.replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(false) + "_after.java");
  }

  protected final void doTestNamespaceCompletion(@NotNull String... extraNamespaces)
    throws IOException {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> variants = myFixture.getLookupElementStrings();
    assertNotNull(variants);
    List<String> expectedVariants = new ArrayList<>();

    expectedVariants.add(SdkConstants.ANDROID_URI);
    expectedVariants.add(SdkConstants.TOOLS_URI);
    expectedVariants.add(SdkConstants.AUTO_URI);

    expectedVariants.addAll(Arrays.asList(extraNamespaces));

    Collections.sort(expectedVariants);
    assertEquals(expectedVariants, variants);
  }

  /**
   * Loads file, invokes code completion at &lt;caret&gt; marker and verifies the resulting completion variants as strings.
   */
  protected final void doTestCompletionVariants(String fileName, String... variants) throws Throwable {
    List<String> lookupElementStrings = getCompletionElements(fileName);
    assertNotNull(lookupElementStrings);
    assertSameElements(lookupElementStrings, variants);
  }

  protected final void doTestCompletionVariantsContains(String fileName, String... variants) throws Throwable {
    List<String> lookupElementStrings = getCompletionElements(fileName);
    assertNotNull(lookupElementStrings);
    assertContainsElements(lookupElementStrings, variants);
  }

  /**
   * Loads file, invokes code completion at &lt;caret&gt; marker and verifies the completions that are presented to the user as strings.
   * Use {@link #doTestCompletionVariants} to check the strings used for the actual completion.
   */
  protected final void doTestPresentableCompletionVariants(@NotNull String fileName, @NotNull String... variants) throws Throwable {
    List<String> lookupElementStrings = getPresentableCompletionElements(fileName);
    assertNotNull(lookupElementStrings);
    assertSameElements(lookupElementStrings, variants);
  }

  private List<String> getCompletionElements(String fileName) throws IOException {
    VirtualFile file = copyFileToProject(fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    return myFixture.getLookupElementStrings();
  }

  @NotNull
  private List<String> getPresentableCompletionElements(@NotNull String fileName) throws IOException {
    VirtualFile file = copyFileToProject(fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    return Arrays.stream(myFixture.getLookupElements())
      .map(AndroidDomTestCase::toPresentableText)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Nullable
  private static String toPresentableText(@NotNull LookupElement element) {
    LookupElementPresentation presentation = new LookupElementPresentation();
    element.renderElement(presentation);
    return presentation.getItemText();
  }

  protected final void doTestHighlighting() throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    doTestHighlighting(getTestName(true) + ".xml");
  }

  /**
   * Creates a virtual file from {@code fileName} and calls {@code doTestHighlighting(VirtualFile virtualFile)} passing it as a parameter
   */
  protected final void doTestHighlighting(String fileName) throws Throwable {
    doTestHighlighting(copyFileToProject(fileName));
  }

  /**
   * Loads a virtual file and checks whether result of highlighting correspond to XML-like markers left in it. Format of the markers is best
   * described by an example, check the usages of the function to find out.
   */
  protected final void doTestHighlighting(VirtualFile virtualFile) throws Throwable {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  protected final void doTestJavaHighlighting(String aPackage) throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    String fileName = getTestName(false) + ".java";
    VirtualFile virtualFile = copyFileToProject(fileName, "src/" + aPackage.replace('.', '/') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  protected final void doTestCompletion() throws Throwable {
    doTestCompletion(true);
  }

  protected final void doTestCompletion(boolean lowercaseFirstLetter) throws Throwable {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    toTestCompletion(getTestName(lowercaseFirstLetter) + ".xml", getTestName(lowercaseFirstLetter) + "_after.xml");
  }

  /**
   * Loads first file, puts caret on the &lt;caret&gt; marker, invokes code completion. If running the code completion results in returning
   * only one completion variant, chooses it to complete code at the caret.
   */
  protected final void toTestCompletion(String fileBefore, String fileAfter) throws Throwable {
    VirtualFile file = copyFileToProject(fileBefore);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(myTestFolder + '/' + fileAfter);
  }

  /**
   * Variant of {@link #toTestCompletion(String, String)} that chooses the first completion variant
   * when several possibilities are available.
   */
  protected final void toTestFirstCompletion(@NotNull String fileBefore, @NotNull String fileAfter) throws Throwable {
    VirtualFile file = copyFileToProject(fileBefore);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + fileAfter);
  }

  protected final void doTestSpellcheckerQuickFixes() throws IOException {
    //noinspection unchecked
    myFixture.enableInspections(SpellCheckingInspection.class);
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    List<IntentionAction> fixes = highlightAndFindQuickFixes(null);
    assertEquals(2, fixes.size());
    assertInstanceOf(((QuickFixWrapper)fixes.get(0)).getFix(), RenameTo.class);
    assertInstanceOf(((QuickFixWrapper)fixes.get(1)).getFix(), SaveTo.class);
  }

  /**
   * Return a destination for files to be copied by {@link #copyFileToProject(String)}
   */
  protected abstract String getPathToCopy(String testFileName);

  protected final VirtualFile copyFileToProject(String path) throws IOException {
    return copyFileToProject(path, getPathToCopy(path));
  }

  protected final VirtualFile copyFileToProject(String from, String to) throws IOException {
    return myFixture.copyFileToProject(myTestFolder + '/' + from, to);
  }

  protected final void doTestAndroidPrefixCompletion(@Nullable String prefix) throws IOException {
    // TODO: Kill getTestName, make test classes specify the golden file explicitly.
    VirtualFile f = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.complete(CompletionType.BASIC);
    List<String> strs = myFixture.getLookupElementStrings();
    if (prefix != null) {
      assertNotNull(strs);
      assertEquals(strs.get(0), prefix);
    }
    else if (strs != null && !strs.isEmpty()) {
      String first = strs.get(0);
      assertFalse(first.endsWith(":"));
    }
  }

  protected final void doTestExternalDoc(String expectedPart) {
    PsiElement originalElement = myFixture.getFile().findElementAt(
      myFixture.getEditor().getCaretModel().getOffset());
    PsiElement docTargetElement = DocumentationManager.getInstance(getProject()).
      findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    List<String> urls = provider.getUrlFor(docTargetElement, originalElement);
    String doc = ((ExternalDocumentationProvider)provider).fetchExternalDocumentation(myFixture.getProject(), docTargetElement, urls);
    assertNotNull(doc);
    assertTrue("Can't find " + expectedPart + " in " + doc, doc.contains(expectedPart));
  }

  protected final void doTestDoc(String expectedPart) {
    PsiElement originalElement = myFixture.getFile().findElementAt(
      myFixture.getEditor().getCaretModel().getOffset());
    PsiElement docTargetElement = DocumentationManager.getInstance(getProject()).
      findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    String doc = provider.generateDoc(docTargetElement, originalElement);
    assertNotNull(doc);
    assertTrue("Can't find " + expectedPart + " in " + doc, doc.contains(expectedPart));
  }

  protected final List<IntentionAction> highlightAndFindQuickFixes(Class<?> aClass) {
    List<HighlightInfo> infos = myFixture.doHighlighting();
    List<IntentionAction> actions = new ArrayList<>();

    for (HighlightInfo info : infos) {
      List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = info.quickFixActionRanges;

      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          IntentionAction action = pair.getFirst().getAction();
          if (aClass == null || action.getClass() == aClass) {
            actions.add(action);
          }
        }
      }
    }
    return actions;
  }

  protected final void doTestOnClickQuickfix(VirtualFile file) {
    myFixture.configureFromExistingVirtualFile(file);
    List<IntentionAction> actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEquals(1, actions.size());
    IntentionAction action = actions.get(0);
    assertInstanceOf(action, AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    WriteCommandAction.runWriteCommandAction(
      null, () -> ((AndroidMissingOnClickHandlerInspection.MyQuickFix)action).doApplyFix(getProject()));
    myFixture.checkResultByFile(myTestFolder + "/onClickIntention.xml");
  }
}

