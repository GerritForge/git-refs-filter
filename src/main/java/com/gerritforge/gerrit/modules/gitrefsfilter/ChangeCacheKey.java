// Copyright (C) 2025 GerritForge, Inc.
//
// Licensed under the BSL 1.1 (the "License");
// you may not use this file except in compliance with the License.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.gerrit.modules.gitrefsfilter;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import java.util.concurrent.atomic.AtomicReference;

import com.gerritforge.gerrit.modules.gitrefsfilter.AutoValue_ChangeCacheKey;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@AutoValue
public abstract class ChangeCacheKey {
  /* `repo` and `changeId` need to be part of the cache key because the
  Loader requires them in order to fetch the relevant changeNote. */
  private final AtomicReference<Repository> repo = new AtomicReference<>();

  public final Repository repo() {
    return repo.get();
  }

  public abstract Change.Id changeId();

  @Nullable
  public abstract ObjectId changeRevision();

  public abstract Project.NameKey project();

  static ChangeCacheKey create(
      Repository repo,
      Change.Id changeId,
      @Nullable ObjectId changeRevision,
      Project.NameKey project) {
    ChangeCacheKey cacheKey = new AutoValue_ChangeCacheKey(changeId, changeRevision, project);
    cacheKey.repo.set(repo);
    return cacheKey;
  }
}
