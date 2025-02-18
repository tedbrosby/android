/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.utils.SparseArray;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.ide.impl.ProjectViewSelectInPaneTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static com.android.SdkConstants.EXT_GRADLE;

/**
 * Static utility methods pertaining to templates for projects, modules, and activities.
 */
@SuppressWarnings("restriction") // WST API
public class TemplateUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.DomUtilities");
  private static final Pattern WINDOWS_NEWLINE = Pattern.compile("\r\n");

  /**
   * Creates a Java class name out of the given string, if possible. For
   * example, "My Project" becomes "MyProject", "hello" becomes "Hello",
   * "Java's" becomes "Java", and so on.
   *
   * @param string the string to be massaged into a Java class
   * @return the string as a Java class, or null if a class name could not be
   * extracted
   */
  @Nullable
  public static String extractClassName(@NotNull String string) {
    StringBuilder sb = new StringBuilder(string.length());
    int n = string.length();

    int i = 0;
    for (; i < n; i++) {
      char c = Character.toUpperCase(string.charAt(i));
      if (Character.isJavaIdentifierStart(c)) {
        sb.append(c);
        i++;
        break;
      }
    }
    if (sb.length() > 0) {
      for (; i < n; i++) {
        char c = string.charAt(i);
        if (Character.isJavaIdentifierPart(c)) {
          sb.append(c);
        }
      }

      return sb.toString();
    }

    return null;
  }

  /**
   * Strips the given suffix from the given file, provided that the file name ends with
   * the suffix.
   *
   * @param file   the file to strip from
   * @param suffix the suffix to strip out
   * @return the file without the suffix at the end
   */
  public static File stripSuffix(@NotNull File file, @NotNull String suffix) {
    if (file.getName().endsWith(suffix)) {
      String name = file.getName();
      name = name.substring(0, name.length() - suffix.length());
      File parent = file.getParentFile();
      if (parent != null) {
        return new File(parent, name);
      }
      else {
        return new File(name);
      }
    }

    return file;
  }

  /**
   * Converts a CamelCase word into an underlined_word
   *
   * @param string the CamelCase version of the word
   * @return the underlined version of the word
   */
  public static String camelCaseToUnderlines(String string) {
    if (string.isEmpty()) {
      return string;
    }

    StringBuilder sb = new StringBuilder(2 * string.length());
    int n = string.length();
    boolean lastWasUpperCase = Character.isUpperCase(string.charAt(0));
    for (int i = 0; i < n; i++) {
      char c = string.charAt(i);
      boolean isUpperCase = Character.isUpperCase(c);
      if (isUpperCase && !lastWasUpperCase) {
        sb.append('_');
      }
      lastWasUpperCase = isUpperCase;
      c = Character.toLowerCase(c);
      sb.append(c);
    }

    return sb.toString();
  }

  /**
   * Converts an underlined_word into a CamelCase word
   *
   * @param string the underlined word to convert
   * @return the CamelCase version of the word
   */
  public static String underlinesToCamelCase(String string) {
    StringBuilder sb = new StringBuilder(string.length());
    int n = string.length();

    int i = 0;
    boolean upcaseNext = true;
    for (; i < n; i++) {
      char c = string.charAt(i);
      if (c == '_') {
        upcaseNext = true;
      }
      else {
        if (upcaseNext) {
          c = Character.toUpperCase(c);
        }
        upcaseNext = false;
        sb.append(c);
      }
    }

    return sb.toString();
  }

  /**
   * Returns a list of known API names
   *
   * @return a list of string API names, starting from 1 and up through the
   * maximum known versions (with no gaps)
   */
  @NotNull
  public static String[] getKnownVersions() {
    final AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    assert sdkData != null;
    int max = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
    IAndroidTarget[] targets = sdkData.getTargets();
    SparseArray<IAndroidTarget> apiTargets = null;
    for (IAndroidTarget target : targets) {
      if (target.isPlatform()) {
        AndroidVersion version = target.getVersion();
        if (!version.isPreview()) {
          int apiLevel = version.getApiLevel();
          max = Math.max(max, apiLevel);
          if (apiLevel > SdkVersionInfo.HIGHEST_KNOWN_API) {
            if (apiTargets == null) {
              apiTargets = new SparseArray<>();
            }
            apiTargets.put(apiLevel, target);
          }
        }
      }
    }

    String[] versions = new String[max];
    for (int api = 1; api <= max; api++) {
      String name = SdkVersionInfo.getAndroidName(api);

      // noinspection ConstantConditions
      if (name == null) {
        if (apiTargets != null) {
          IAndroidTarget target = apiTargets.get(api);
          if (target != null) {
            name = AndroidSdkUtils.getTargetLabel(target);
          }
        }
        if (name == null) {
          name = String.format("API %1$d", api);
        }
      }
      versions[api - 1] = name;
    }

    return versions;
  }

  /**
   * Returns the element children of the given element
   *
   * @param element the parent element
   * @return a list of child elements, possibly empty but never null
   */
  @NotNull
  public static List<Element> getChildren(@NotNull Element element) {
    // Convenience to avoid lots of ugly DOM access casting
    NodeList children = element.getChildNodes();
    // An iterator would have been more natural (to directly drive the child list
    // iteration) but iterators can't be used in enhanced for loops...
    List<Element> result = new ArrayList<>(children.getLength());
    for (int i = 0, n = children.getLength(); i < n; i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element child = (Element)node;
        result.add(child);
      }
    }

    return result;
  }

  public static void reformatAndRearrange(@NotNull final Project project, @NotNull final Iterable<File> files) {
    WriteCommandAction.runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

        for (File file : files) {
          if (file.isFile()) {
            VirtualFile virtualFile = localFileSystem.findFileByIoFile(file);
            assert virtualFile != null;

            reformatAndRearrange(project, virtualFile);
          }
        }
      }
    });
  }

  /**
   * Reformats and rearranges the part of the File concerning the PsiElement received
   *
   * @param project    The project which contains the given element
   * @param psiElement The element to be reformated and rearranged
   */
  public static void reformatAndRearrange(@NotNull Project project, @NotNull PsiElement psiElement) {
    reformatAndRearrange(project, psiElement.getContainingFile().getVirtualFile(), psiElement, true);
  }

  /**
   * Reformats and rearranges the entire File
   */
  public static void reformatAndRearrange(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    reformatAndRearrange(project, virtualFile, null, false);
  }

  /**
   * Reformats and rearranges the file (entirely or part of it)
   *
   * Note: reformatting the PSI file requires that this be wrapped in a write command.
   *
   * @param project            The project which contains the given element
   * @param virtualFile        Virtual file to be reformatted and rearranged, if null, the entire file will be considered
   * @param psiElement         The element in the file to be reformatted and rearranged
   * @param keepDocumentLocked True if the document will still be modified in the same write action
   */
  private static void reformatAndRearrange(@NotNull Project project,
                                           @NotNull VirtualFile virtualFile,
                                           @Nullable PsiElement psiElement,
                                           boolean keepDocumentLocked) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (Objects.equals(virtualFile.getExtension(), EXT_GRADLE)) {
      // Do not format Gradle files. Otherwise we get spurious "Gradle files have changed since last project sync" warnings that make UI
      // tests flaky.
      return;
    }

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);

    if (document == null) {
      // The file could be a binary file with no editing support...
      return;
    }

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    psiDocumentManager.commitDocument(document);

    PsiFile psiFile = psiDocumentManager.getPsiFile(document);
    if (psiFile != null) {
      TextRange textRange = psiElement == null ? psiFile.getTextRange() : psiElement.getTextRange();
      CodeStyleManager.getInstance(project).reformatRange(psiFile, textRange.getStartOffset(), textRange.getEndOffset());

      // The textRange of psiElement in the file can change after reformatting
      textRange = psiElement == null ? psiFile.getTextRange() : psiElement.getTextRange();
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
      ArrangementEngine.getInstance().arrange(psiFile, Collections.singleton(textRange));

      if (keepDocumentLocked) {
        psiDocumentManager.commitDocument(document);
      }
    }
  }

  /**
   * Opens the specified files in the editor
   *
   * @param project The project which contains the given file.
   * @param files   The files on disk.
   * @param select  If true, select the last (topmost) file in the project view
   * @return true if all files were opened
   */
  public static boolean openEditors(@NotNull Project project, @NotNull Collection<File> files, boolean select) {
    if (!files.isEmpty()) {
      boolean result = true;
      VirtualFile last = null;
      for (File file : files) {
        if (file.exists()) {
          VirtualFile vFile = VfsUtil.findFileByIoFile(file, true);
          if (vFile != null) {
            result &= openEditor(project, vFile);
            last = vFile;
          }
          else {
            result = false;
          }
        }
      }

      if (select && last != null) {
        selectEditor(project, last);
      }

      return result;
    }

    return false;
  }

  /**
   * Opens the specified file in the editor
   *
   * @param project The project which contains the given file.
   * @param vFile   The file to open
   */
  public static boolean openEditor(@NotNull Project project, @NotNull VirtualFile vFile) {
    OpenFileDescriptor descriptor;
    if (FileTypeRegistry.getInstance().isFileOfType(vFile, StdFileTypes.XML) && AndroidEditorSettings.getInstance().getGlobalState().isPreferXmlEditor()) {
      descriptor = new OpenFileDescriptor(project, vFile, 0);
    }
    else {
      descriptor = new OpenFileDescriptor(project, vFile);
    }
    return !FileEditorManager.getInstance(project).openEditor(descriptor, true).isEmpty();
  }

  /**
   * Selects the specified file in the project view.
   * <b>Note:</b> Must be called with read access.
   *
   * @param project the project
   * @param file    the file to select
   */
  public static void selectEditor(Project project, VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile != null) {
      AbstractProjectViewPane currentPane = ProjectView.getInstance(project).getCurrentProjectViewPane();
      if (currentPane != null) {
        new ProjectViewSelectInPaneTarget(project, currentPane, true).select(psiFile, false);
      }
    }
  }

  /**
   * Returns the contents of {@code file}, or {@code null} if an {@link IOException} occurs.
   * If an {@link IOException} occurs and {@code warnIfNotExists} is {@code true}, logs a warning.
   *
   * @throws AssertionError if {@code file} is not absolute
   */
  @Nullable
  public static String readTextFromDisk(@NotNull File file, boolean warnIfNotExists) {
    assert file.isAbsolute();
    try {
      return Files.toString(file, Charsets.UTF_8);
    }
    catch (IOException e) {
      if (warnIfNotExists) {
        LOG.warn(e);
      }
      return null;
    }
  }

  /**
   * Returns the contents of {@code file}, or {@code null} if an {@link IOException} occurs.
   * If an {@link IOException} occurs, logs a warning.
   *
   * @throws AssertionError if {@code file} is not absolute
   */
  @Nullable
  public static String readTextFromDisk(@NotNull File file) {
    return readTextFromDisk(file, true);
  }

  /**
   * Reads the given file as text (or the current contents of the edited buffer of the file, if open and not saved.)
   *
   * @param file The file to read.
   * @return the contents of the file as text, or null if for some reason it couldn't be read
   */
  @Nullable
  public static String readTextFromDocument(@NotNull final Project project, @NotNull File file) {
    assert project.isInitialized();
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (vFile == null) {
      LOG.debug("Cannot find file " + file.getPath() + " in the VFS");
      return null;
    }
    return readTextFromDocument(project, vFile);
  }

  /**
   * Reads the given file as text (or the current contents of the edited buffer of the file, if open and not saved.)
   *
   * @param file The file to read.
   * @return the contents of the file as text, or null if for some reason it couldn't be read
   */
  @Nullable
  public static String readTextFromDocument(@NotNull final Project project, @NotNull final VirtualFile file) {
    assert project.isInitialized();
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Nullable
      @Override
      public String compute() {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        return document != null ? document.getText() : null;
      }
    });
  }

  /**
   * Replaces the contents of the given file with the given string. Outputs
   * text in UTF-8 character encoding. The file is created if it does not
   * already exist.
   */
  public static void writeTextFile(@NotNull Object requestor, @Nullable String contents, @NotNull File to) throws IOException {
    if (contents == null) {
      return;
    }
    VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(to);
    if (vf == null) {
      // Creating a new file
      VirtualFile parentDir = checkedCreateDirectoryIfMissing(to.getParentFile());
      vf = parentDir.createChildData(requestor, to.getName());
    }
    Document document = FileDocumentManager.getInstance().getDocument(vf);
    if (document != null) {
      document.setText(WINDOWS_NEWLINE.matcher(contents).replaceAll("\n"));
      FileDocumentManager.getInstance().saveDocument(document);
    }
    else {
      vf.setBinaryContent(contents.getBytes(Charsets.UTF_8), -1, -1, requestor);
    }
  }

  /**
   * Creates a directory for the given file and returns the VirtualFile object.
   *
   * @return virtual file object for the given path. It can never be null.
   */
  @NotNull
  public static VirtualFile checkedCreateDirectoryIfMissing(final @NotNull File directory) throws IOException {
    return WriteCommandAction.runWriteCommandAction(null, new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        VirtualFile dir = VfsUtil.createDirectoryIfMissing(directory.getAbsolutePath());
        if (dir == null) {
          throw new IOException("Unable to create " + directory.getAbsolutePath());
        }
        else {
          return dir;
        }
      }
    });
  }

  /**
   * Find the first parent directory that exists and check if this directory is writeable.
   *
   * @throws IOException if the directory is not writable.
   */
  public static void checkDirectoryIsWriteable(@NotNull File directory) throws IOException {
    while (!directory.exists() || !directory.isDirectory()) {
      directory = directory.getParentFile();
    }
    if (!directory.canWrite()) {
      throw new IOException("Cannot write to folder: " + directory.getAbsolutePath());
    }
  }

  /**
   * Returns true iff the given file has the given extension (with or without .)
   */
  public static boolean hasExtension(File file, String extension) {
    String noDotExtension = extension.startsWith(".") ? extension.substring(1) : extension;
    return Files.getFileExtension(file.getName()).equalsIgnoreCase(noDotExtension);
  }
}
