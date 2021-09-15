// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.modules.gitrefsfilter;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.access.CoreOrPluginProjectPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class ForProjectWrapper extends ForProject {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ForProject defaultForProject;
  private final Project.NameKey project;
  private final ChangeNotes.Factory changeNotesFactory;

  public interface Factory {
    ForProjectWrapper get(ForProject defaultForProject, Project.NameKey project);
  }

  @Inject
  public ForProjectWrapper(
      ChangeNotes.Factory changeNotesFactory,
      @Assisted ForProject defaultForProject,
      @Assisted Project.NameKey project) {
    this.defaultForProject = defaultForProject;
    this.project = project;
    this.changeNotesFactory = changeNotesFactory;
  }

  @Override
  public ForRef ref(String ref) {
    return defaultForProject.ref(ref);
  }

  @Override
  public void check(CoreOrPluginProjectPermission perm)
      throws AuthException, PermissionBackendException {
    defaultForProject.check(perm);
  }

  @Override
  public <T extends CoreOrPluginProjectPermission> Set<T> test(Collection<T> permSet)
      throws PermissionBackendException {
    return defaultForProject.test(permSet);
  }

  @Override
  public Collection<Ref> filter(Collection<Ref> refs, Repository repo, RefFilterOptions opts)
      throws PermissionBackendException {
    return defaultForProject
        .filter(refs, repo, opts)
        .parallelStream()
        .filter((ref) -> !ref.getName().startsWith(RefNames.REFS_USERS))
        .filter(
            (ref) -> {
              String refName = ref.getName();
              return (!isChangeRef(refName)
                  || (!isChangeMetaRef(refName) && isOpen(repo, refName)));
            })
        .collect(Collectors.toList());
  }

  private boolean isChangeRef(String changeKey) {
    return changeKey.startsWith("refs/changes");
  }

  private boolean isChangeMetaRef(String changeKey) {
    return isChangeRef(changeKey) && changeKey.endsWith("/meta");
  }

  private boolean isOpen(Repository repo, String refName) {
    try {
      Change.Id changeId = Change.Id.fromRef(refName);
      ChangeNotes changeNotes = changeNotesFactory.create(repo, project, changeId);
      return changeNotes.getChange().getStatus().isOpen();
    } catch (NoSuchChangeException e) {
      logger.atWarning().withCause(e).log(
          "Ref %s refers to a non-existent change: hiding from the advertised refs", refName);
      return false;
    }
  }

  @Override
  public BooleanCondition testCond(CoreOrPluginProjectPermission perm) {
    return defaultForProject.testCond(perm);
  }

  @Override
  public String resourcePath() {
    return defaultForProject.resourcePath();
  }
}
