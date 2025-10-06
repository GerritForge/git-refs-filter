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
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Change.Id;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.access.CoreOrPluginProjectPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

public class ForProjectWrapper extends ForProject {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LoadingCache<ChangeCacheKey, Boolean> openChangesCache;
  private final LoadingCache<ChangeCacheKey, Long> changesTsCache;
  private final ForProject defaultForProject;
  private final Project.NameKey project;
  private final FilterRefsConfig config;
  private long closedChangesGraceTime;

  public interface Factory {
    ForProjectWrapper get(ForProject defaultForProject, Project.NameKey project);
  }

  @Inject
  public ForProjectWrapper(
      FilterRefsConfig config,
      @Named(OPEN_CHANGES_CACHE) LoadingCache<ChangeCacheKey, Boolean> openChangesCache,
      @Named(CHANGES_CACHE_TS) LoadingCache<ChangeCacheKey, Long> changesTsCache,
      @Assisted ForProject defaultForProject,
      @Assisted Project.NameKey project)
      throws NoSuchProjectException {
    this.openChangesCache = openChangesCache;
    this.changesTsCache = changesTsCache;
    this.defaultForProject = defaultForProject;
    this.project = project;
    this.config = config;
    this.closedChangesGraceTime = config.getClosedChangeGraceTimeSec(project);
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
    Map<Optional<Id>, ObjectId> changeRevisions =
        refs.stream()
            .filter(ref -> ref.getName().endsWith("/meta"))
            .collect(Collectors.toMap(ForProjectWrapper::changeIdFromRef, Ref::getObjectId));
    RefDatabase refDb = repo.getRefDatabase();
    return defaultForProject.filter(refs, repo, opts).parallelStream()
        .filter(ref -> !ref.getName().startsWith(RefNames.REFS_USERS))
        .filter(ref -> !ref.getName().startsWith(RefNames.REFS_CACHE_AUTOMERGE))
        .filter(config::isRefToShow)
        .filter(
            (ref) -> {
              Optional<Id> changeId = changeIdFromRef(ref);
              Optional<ObjectId> changeRevision =
                  changeId.flatMap(cid -> Optional.ofNullable(changeRevisions.get(changeId)));
              if (!changeRevision.isPresent()) {
                changeRevision = changeRevisionFromRefDb(refDb, changeId);
              }
              String refName = ref.getName();
              return (!changeId.isPresent()
                  || !changeRevision.isPresent()
                  || (!RefNames.isNoteDbMetaRef(refName)
                      && (isOpen(repo, changeId.get(), changeRevision.get())
                          || isRecent(repo, changeId.get(), changeRevision.get()))));
            })
        .collect(Collectors.toList());
  }

  private static Optional<ObjectId> changeRevisionFromRefDb(
      RefDatabase refDb, Optional<Change.Id> changeId) {
    return changeId.flatMap(cid -> exactRefUnchecked(refDb, cid)).map(Ref::getObjectId);
  }

  private static Optional<Ref> exactRefUnchecked(RefDatabase refDb, Change.Id changeId) {
    try {
      return Optional.ofNullable(refDb.exactRef(RefNames.changeMetaRef(changeId)));
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Error looking up change '%d' meta-ref from refs db.", changeId.get());
      return Optional.empty();
    }
  }

  private static Optional<Change.Id> changeIdFromRef(Ref ref) {
    return Optional.ofNullable(Change.Id.fromRef(ref.getName()));
  }

  private boolean isOpen(Repository repo, Change.Id changeId, ObjectId changeRevision) {
    try {
      return openChangesCache.get(ChangeCacheKey.create(repo, changeId, changeRevision, project));
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Error getting change '%d' from the cache. Do not hide from the advertised refs",
          changeId.get());
      return true;
    }
  }

  private boolean isRecent(Repository repo, Change.Id changeId, ObjectId changeRevision) {
    try {
      Timestamp cutOffTs =
          Timestamp.from(
              Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(closedChangesGraceTime));
      return changesTsCache
              .get(ChangeCacheKey.create(repo, changeId, changeRevision, project))
              .longValue()
          >= cutOffTs.getTime();

    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log(
          "Error getting change '%d' from the cache. Do not hide from the advertised refs",
          changeId.get());
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
