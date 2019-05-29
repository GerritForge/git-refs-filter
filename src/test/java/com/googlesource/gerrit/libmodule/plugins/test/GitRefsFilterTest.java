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

package com.googlesource.gerrit.libmodule.plugins.test;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.inject.Module;
import com.googlesource.gerrit.modules.gitrefsfilter.RefsFilterModule;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@Sandboxed
public class GitRefsFilterTest extends AbstractGitDaemonTest {

  @Override
  public Module createModule() {
    return new RefsFilterModule();
  }

  @Before
  public void setup() throws Exception {
    createFilteredRefsGroup();
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldNotSeeAbandonedChangesRefs() throws Exception {
    createChangeAndAbandon();

    assertThat(getRefs(cloneProjectChangesRefs(user))).isEmpty();
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldSeeOpenChangesRefs() throws Exception {
    createChange();

    assertThat(getRefs(cloneProjectChangesRefs(user))).isNotEmpty();
  }

  @Test
  public void testAdminUserShouldSeeAbandonedChangesRefs() throws Exception {
    createChangeAndAbandon();

    assertThat(getRefs(cloneProjectChangesRefs(admin))).isNotEmpty();
  }
}
