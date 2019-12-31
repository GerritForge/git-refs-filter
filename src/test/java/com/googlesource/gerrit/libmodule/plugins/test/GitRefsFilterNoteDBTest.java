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

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.inject.Module;
import com.googlesource.gerrit.modules.gitrefsfilter.RefsFilterModule;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.NOTE_DB;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;

@NoHttpd
@Sandboxed
public class GitRefsFilterNoteDBTest extends AbstractGitDaemonTest {

  @Override
  public Module createModule() {
    return new RefsFilterModule();
  }

  @Before
  public void setup() throws Exception {
    notesMigration.setChangePrimaryStorage(NOTE_DB);
    notesMigration.setDisableChangeReviewDb(true);
    notesMigration.setReadChanges(true);
    notesMigration.setWriteChanges(true);
    createFilteredRefsGroup();
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldNotSeeAbandonedChangesRefs() throws Exception {
    createChangeAndAbandon();

    assertThat(getRefs(cloneProjectChangesRefs(user))).hasSize(0);
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldSeeOpenChangesRefsButNoteMetaRefs() throws Exception {
    createChange();

    assertThat(getRefsString(user)).containsExactlyElementsIn(Collections.singletonList(CHANGE_REF));
  }

  @Test
  public void testAdminUserShouldSeeAbandonedChangesRefs() throws Exception {
    createChangeAndAbandon();

    assertThat(getRefsString(admin)).containsExactlyElementsIn(Arrays.asList(CHANGE_REF, CHANGE_REF_META));
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldSeeClosedChangeThatIsRecentEnough()
      throws Exception {
    filterOnlyClosedChangesOlderThan("1 month");

    createChangeAndAbandon();

    assertThat(getRefsString(user)).containsExactlyElementsIn(Collections.singletonList(CHANGE_REF));
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldNotSeeClosedChangeThatIsNotRecentEnough()
      throws Exception {

    filterOnlyClosedChangesOlderThan("500 milliseconds");

    createChangeAndAbandon();
    Thread.sleep(500);

    assertThat(getRefs(cloneProjectChangesRefs(user))).hasSize(0);
  }
}
