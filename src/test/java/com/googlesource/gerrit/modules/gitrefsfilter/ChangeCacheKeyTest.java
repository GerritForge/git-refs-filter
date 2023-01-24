// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class ChangeCacheKeyTest {
  private static final String REPO_NAME = "test_repo";
  private static final Change.Id ID = Change.id(10000);
  private static final ObjectId CHANGE_REVISION = ObjectId.zeroId();
  private static final NameKey TEST_REPO = Project.nameKey(REPO_NAME);

  @Test
  public void shouldExcludeRepoFieldDuringEqualsCalculation() {
    ChangeCacheKey cacheKey1 =
        ChangeCacheKey.create(mock(Repository.class), ID, CHANGE_REVISION, TEST_REPO);
    ChangeCacheKey cacheKey2 =
        ChangeCacheKey.create(mock(Repository.class), ID, CHANGE_REVISION, TEST_REPO);
    assertThat(cacheKey1).isEqualTo(cacheKey2);
  }

  @Test
  public void shouldExcludeRepoFieldDuringHashCodeCalculation() {
    ChangeCacheKey cacheKey1 =
        ChangeCacheKey.create(mock(Repository.class), ID, CHANGE_REVISION, TEST_REPO);
    ChangeCacheKey cacheKey2 =
        ChangeCacheKey.create(mock(Repository.class), ID, CHANGE_REVISION, TEST_REPO);
    assertThat(cacheKey1.hashCode()).isEqualTo(cacheKey2.hashCode());
  }
}
