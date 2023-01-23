// Copyright (C) 2022 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@AutoValue
public abstract class ChangeCacheKey {
  /* `repo` and `changeId` need to be part of the cache key because the
  Loader requires them in order to fetch the relevant changeNote. */
  public abstract Repository repo();

  public abstract Change.Id changeId();

  @Nullable
  public abstract ObjectId changeRevision();

  public abstract Project.NameKey project();

  static ChangeCacheKey create(
      Repository repo,
      Change.Id changeId,
      @Nullable ObjectId changeRevision,
      Project.NameKey project) {
    return new AutoValue_ChangeCacheKey(repo, changeId, changeRevision, project);
  }

  @Override
  public final boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ChangeCacheKey) {
      ChangeCacheKey that = (ChangeCacheKey) o;
      return this.changeId().equals(that.changeId())
          && (this.changeRevision() == null
              ? that.changeRevision() == null
              : this.changeRevision().equals(that.changeRevision()))
          && this.project().equals(that.project());
    }
    return false;
  }

  @Override
  public final int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= changeId().hashCode();
    h$ *= 1000003;
    h$ ^= (changeRevision() == null) ? 0 : changeRevision().hashCode();
    h$ *= 1000003;
    h$ ^= project().hashCode();
    return h$;
  }
}
