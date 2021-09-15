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
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class ForProjectWrapper extends ForProject {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final ForProject defaultForProject;
  private final NameKey project;
  private final ChangeNotes.Factory changeNotesFactory;
  private final Provider<ReviewDb> dbProvider;

  public interface Factory {
    ForProjectWrapper get(ForProject defaultForProject, Project.NameKey project);
  }

  @Inject
  public ForProjectWrapper(
      ChangeNotes.Factory changeNotesFactory,
      Provider<ReviewDb> dbProvider,
      @Assisted ForProject defaultForProject,
      @Assisted Project.NameKey project) {
    this.defaultForProject = defaultForProject;
    this.project = project;
    this.changeNotesFactory = changeNotesFactory;
    this.dbProvider = dbProvider;
  }

  @Override
  public ForRef ref(String ref) {
    return defaultForProject.ref(ref);
  }

  @Override
  public void check(ProjectPermission perm) throws AuthException, PermissionBackendException {
    defaultForProject.check(perm);
  }

  @Override
  public Set<ProjectPermission> test(Collection<ProjectPermission> permSet)
      throws PermissionBackendException {
    return defaultForProject.test(permSet);
  }

  @Override
  public Map<String, Ref> filter(Map<String, Ref> refs, Repository repo, RefFilterOptions opts)
      throws PermissionBackendException {
    Map<String, Ref> filteredRefs = new HashMap<>();
    Map<String, Ref> defaultFilteredRefs =
        defaultForProject.filter(refs, repo, opts); // FIXME: can we filter the closed refs here?

    for (String changeKey : defaultFilteredRefs.keySet()) {
      String refName = defaultFilteredRefs.get(changeKey).getName();
      if (refName.startsWith(RefNames.REFS_USERS)) {
        continue;
      }
      if (!isChangeRef(changeKey) || (isOpen(repo, refName) && !isChangeMetaRef(changeKey))) {
        filteredRefs.put(changeKey, defaultFilteredRefs.get(changeKey));
      }
    }

    return filteredRefs;
  }

  private boolean isChangeRef(String changeKey) {
    return changeKey.startsWith("refs/changes");
  }

  private boolean isChangeMetaRef(String changeKey) {
    return isChangeRef(changeKey) && changeKey.endsWith("/meta");
  }

  private boolean isOpen(Repository repo, String refName) {
    Change.Id changeId = Change.Id.fromRef(refName);
    ChangeNotes changeNotes;
    try {
      changeNotes = changeNotesFactory.create(dbProvider.get(), repo, project, changeId);
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log(
          "Cannot create change notes for change {}, project {}", changeId, project);
      return false;
    }
    return changeNotes.getChange().getStatus().isOpen();
  }

  @Override
  public BooleanCondition testCond(ProjectPermission perm) {
    return defaultForProject.testCond(perm);
  }

  @Override
  public String resourcePath() {
    return defaultForProject.resourcePath();
  }
}
