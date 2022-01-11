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

import static com.googlesource.gerrit.modules.gitrefsfilter.ChangesTsCache.CHANGES_CACHE_TS;
import static com.googlesource.gerrit.modules.gitrefsfilter.OpenChangesCache.OPEN_CHANGES_CACHE;

import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.access.CoreOrPluginProjectPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class ForProjectWrapper extends ForProject {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LoadingCache<ChangeCacheKey, Boolean> openChangesCache;
  private final LoadingCache<ChangeCacheKey, Long> changesTsCache;
  private final ForProject defaultForProject;
  private final Project.NameKey project;
  private final FilterRefsConfig config;

  public interface Factory {
    ForProjectWrapper get(ForProject defaultForProject, Project.NameKey project);
  }

  @Inject
  public ForProjectWrapper(
      FilterRefsConfig config,
      @Named(OPEN_CHANGES_CACHE) LoadingCache<ChangeCacheKey, Boolean> openChangesCache,
      @Named(CHANGES_CACHE_TS) LoadingCache<ChangeCacheKey, Long> changesTsCache,
      @Assisted ForProject defaultForProject,
      @Assisted Project.NameKey project) {
    this.openChangesCache = openChangesCache;
    this.changesTsCache = changesTsCache;
    this.defaultForProject = defaultForProject;
    this.project = project;
    this.config = config;
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
    Map<Change.Id, ObjectId> changeRevisions =
        refs.stream()
            .filter(ref -> ref.getName().endsWith("/meta"))
            .collect(Collectors.toMap(ForProjectWrapper::changeIdFromRef, Ref::getObjectId));
    return defaultForProject
        .filter(refs, repo, opts)
        .parallelStream()
        .filter(ref -> !ref.getName().startsWith(RefNames.REFS_USERS))
        .filter(ref -> !ref.getName().startsWith(RefNames.REFS_CACHE_AUTOMERGE))
        .filter(config::isRefToShow)
        .filter(
            (ref) -> {
              Change.Id changeId = changeIdFromRef(ref);
              String refName = ref.getName();
              return (!isChangeRef(refName)
                  || (!isChangeMetaRef(refName)
                      && changeId != null
                      && (isOpen(repo, changeId, changeRevisions.get(changeId))
                          || isRecent(repo, changeId, changeRevisions.get(changeId)))));
            })
        .collect(Collectors.toList());
  }

  private static Change.Id changeIdFromRef(Ref ref) {
    return Change.Id.fromRef(ref.getName());
  }

  private boolean isChangeRef(String changeKey) {
    return changeKey.startsWith("refs/changes");
  }

  private boolean isChangeMetaRef(String changeKey) {
    return isChangeRef(changeKey) && changeKey.endsWith("/meta");
  }

  private boolean isOpen(Repository repo, Change.Id changeId, @Nullable ObjectId changeRevision) {
    try {
      return openChangesCache.get(ChangeCacheKey.create(repo, changeId, changeRevision, project));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Error getting change '%d' from the cache. Do not hide from the advertised refs",
          changeId);
      return true;
    }
  }

  private boolean isRecent(Repository repo, Change.Id changeId, @Nullable ObjectId changeRevision) {
    try {
      Long cutOffTs = System.currentTimeMillis() - config.getClosedChangeGraceTimeMsec();
      return changesTsCache
              .get(ChangeCacheKey.create(repo, changeId, changeRevision, project))
              .longValue()
          > cutOffTs;

    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Error getting change '%d' from the cache. Do not hide from the advertised refs",
          changeId);
      return true;
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
