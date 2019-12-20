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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class ForProjectWrapper extends ForProject {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private GitRefsFilterConfig.Factory gitRefsFilterConfigFactory;
  private GitRepositoryManager repositoryManager;
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
      GitRefsFilterConfig.Factory gitRefsFilterConfigFactory,
      GitRepositoryManager gitManager,
      @Assisted ForProject defaultForProject,
      @Assisted Project.NameKey project) {
    this.gitRefsFilterConfigFactory = gitRefsFilterConfigFactory;
    this.repositoryManager = gitManager;
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
    GitRefsFilterConfig gitRefsFilterConfig = gitRefsFilterConfigFactory.create(project);
    Map<String, Ref> filteredRefs = new HashMap<>();
    Map<String, Ref> defaultFilteredRefs =
        defaultForProject.filter(refs, repo, opts); // FIXME: can we filter the closed refs here?
    Set<String> wantedChangesRefs =
        wantedChangesByScan(repo, gitRefsFilterConfig.hideClosedChangesAfterSecs());

    for (String changeKey : defaultFilteredRefs.keySet()) {
      if (!isChangeRef(changeKey)
          || (isOpen(wantedChangesRefs, changeKey) && !isChangeMetaRef(changeKey))) {
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

  private boolean isOpen(Set<String> openChangesRefs, String changeKey) {
    // Parse changeKey as refs/changes/NN/<change num>/PP
    String changeRefWithoutPatchset = changeKey.substring(0, changeKey.lastIndexOf('/') + 1);
    return openChangesRefs.contains(changeRefWithoutPatchset);
  }

  @Override
  public BooleanCondition testCond(ProjectPermission perm) {
    return defaultForProject.testCond(perm);
  }

  @Override
  public String resourcePath() {
    return defaultForProject.resourcePath();
  }

  private Set<String> wantedChangesByScan(Repository repo, long hideClosedChangesAfterSecs) {
    Set<String> result = new HashSet<>();
    Stream<ChangeNotesResult> s;
    try {
      s = changeNotesFactory.scan(repo, dbProvider.get(), project);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", project);
      return Collections.emptySet();
    }

    java.util.Date closedChangesThreshold =
        Date.from(Instant.now().minusSeconds(hideClosedChangesAfterSecs));
    for (ChangeNotesResult notesResult : s.collect(toImmutableList())) {
      Change change = toNotes(notesResult).getChange();
      if (change.getStatus().isOpen()
          || (hideClosedChangesAfterSecs > 0 // short-circuit timestamp evaluation, when not needed
              && newerThanThreshold(change, closedChangesThreshold))) {
        result.add(change.getId().toRefPrefix());
      } else {
        logger.atFine().log("Skipping change %s", change.getChangeId());
      }
    }
    return result;
  }

  private boolean newerThanThreshold(Change change, java.util.Date closedChangesThreshold) {
    boolean isNewer;
    try (Repository repo = repositoryManager.openRepository(project);
        RevWalk walk = new RevWalk(repo)) {
      String changeRef = change.currentPatchSetId().toRefName();
      if (changeRef == null) {
        throw new IOException(
            String.format(
                "Could not find ref name for change %s, patchset %s",
                change.getChangeId(), change.currentPatchSetId()));
      }
      RevCommit revCommit = walk.parseCommit(repo.exactRef(changeRef).getObjectId());
      isNewer = revCommit.getCommitterIdent().getWhen().after(closedChangesThreshold);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Error evaluating change %s time threshold. Assuming change should be visible",
          change.getChangeId());
      isNewer = true;
    }
    return isNewer;
  }

  @Nullable
  private ChangeNotes toNotes(ChangeNotesResult r) {
    if (r.error().isPresent()) {
      logger.atWarning().withCause(r.error().get()).log(
          "Failed to load change %s in %s", r.id(), project);
      return null;
    }
    return r.notes();
  }
}
