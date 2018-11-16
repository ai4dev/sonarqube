/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.scan.filesystem;

import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.AbstractProjectOrModule;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.FileExtensionPredicate;
import org.sonar.api.scanner.fs.InputProject;
import org.sonar.scanner.scan.branch.BranchConfiguration;

/**
 * Store of all files and dirs. Inclusion and
 * exclusion patterns are already applied.
 */
public class InputComponentStore {

  private final SortedSet<String> globalLanguagesCache = new TreeSet<>();
  private final Map<String, SortedSet<String>> languagesCache = new HashMap<>();
  private final Map<String, InputFile> globalInputFileCache = new HashMap<>();
  private final Table<String, String, InputFile> inputFileByModuleCache = TreeBasedTable.create();
  // indexed by key with branch
  private final Map<String, AbstractProjectOrModule> inputModuleCache = new HashMap<>();
  private final Map<String, InputComponent> inputComponents = new HashMap<>();
  private final SetMultimap<String, InputFile> filesByNameCache = LinkedHashMultimap.create();
  private final SetMultimap<String, InputFile> filesByExtensionCache = LinkedHashMultimap.create();
  private final InputProject project;
  private final BranchConfiguration branchConfiguration;

  public InputComponentStore(InputProject project, BranchConfiguration branchConfiguration) {
    this.project = project;
    this.branchConfiguration = branchConfiguration;
  }

  public Collection<InputComponent> all() {
    return inputComponents.values();
  }

  private Stream<DefaultInputFile> allFilesToPublishStream() {
    return inputFileByModuleCache.values().stream()
      .map(f -> (DefaultInputFile) f)
      .filter(DefaultInputFile::isPublished);
  }

  public Iterable<DefaultInputFile> allFilesToPublish() {
    return allFilesToPublishStream()::iterator;
  }

  public Iterable<DefaultInputFile> allChangedFilesToPublish() {
    return allFilesToPublishStream()
      .filter(f -> !branchConfiguration.isShortOrPullRequest() || f.status() != InputFile.Status.SAME)
      ::iterator;
  }

  public Iterable<InputFile> allFiles() {
    return globalInputFileCache.values();
  }

  public InputComponent getByKey(String key) {
    return inputComponents.get(key);
  }

  public Iterable<InputFile> filesByModule(String moduleKey) {
    return inputFileByModuleCache.row(moduleKey).values();
  }

  public InputComponentStore put(String moduleKey, InputFile inputFile) {
    DefaultInputFile file = (DefaultInputFile) inputFile;
    addToLanguageCache(moduleKey, file);
    inputFileByModuleCache.put(moduleKey, file.getModuleRelativePath(), inputFile);
    globalInputFileCache.put(file.getProjectRelativePath(), inputFile);
    inputComponents.put(inputFile.key(), inputFile);
    filesByNameCache.put(inputFile.filename(), inputFile);
    filesByExtensionCache.put(FileExtensionPredicate.getExtension(inputFile), inputFile);
    return this;
  }

  private void addToLanguageCache(String moduleKey, DefaultInputFile inputFile) {
    String language = inputFile.language();
    if (language != null) {
      globalLanguagesCache.add(language);
      languagesCache.computeIfAbsent(moduleKey, k -> new TreeSet<>()).add(language);
    }
  }

  @CheckForNull
  public InputFile getFile(String moduleKey, String relativePath) {
    return inputFileByModuleCache.get(moduleKey, relativePath);
  }

  @CheckForNull
  public InputFile getFile(String relativePath) {
    return globalInputFileCache.get(relativePath);
  }

  @CheckForNull
  public AbstractProjectOrModule getModule(String moduleKeyWithBranch) {
    return inputModuleCache.get(moduleKeyWithBranch);
  }

  public void put(AbstractProjectOrModule inputModule) {
    String key = inputModule.key();
    String keyWithBranch = inputModule.getKeyWithBranch();
    Preconditions.checkNotNull(inputModule);
    Preconditions.checkState(!inputComponents.containsKey(key), "Module '%s' already indexed", key);
    Preconditions.checkState(!inputModuleCache.containsKey(keyWithBranch), "Module '%s' already indexed", keyWithBranch);
    inputComponents.put(key, inputModule);
    inputModuleCache.put(keyWithBranch, inputModule);
  }

  public Iterable<InputFile> getFilesByName(String filename) {
    return filesByNameCache.get(filename);
  }

  public Iterable<InputFile> getFilesByExtension(String extension) {
    return filesByExtensionCache.get(extension);
  }

  public SortedSet<String> getLanguages() {
    return globalLanguagesCache;
  }

  public SortedSet<String> getLanguages(String moduleKey) {
    return languagesCache.getOrDefault(moduleKey, Collections.emptySortedSet());
  }
}
