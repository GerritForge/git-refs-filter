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

import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.inject.Module;
import com.googlesource.gerrit.modules.gitrefsfilter.RefsFilterModule;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.util.FS;
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

    assertThat(getRefs(cloneProjectChangesRefs(user))).hasSize(0);
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldNotSeeUserEdits() throws Exception {
    createChange();
    int changeNum = changeNumOfRef(getChangesRefsAs(admin).get(0));
    gApi.changes().id(changeNum).edit().create();

    assertThat(
            fetchAllRefs(user)
                .filter((ref) -> ref.startsWith(RefNames.REFS_USERS))
                .collect(Collectors.toSet()))
        .isEmpty();
  }

  @Test
  public void testUserWithFilterOutCapabilityShouldSeeOpenChangesRefs() throws Exception {
    createChange();

    assertThat(getRefs(cloneProjectChangesRefs(user))).hasSize(1);
  }

  @Test
  public void testAdminUserShouldSeeAbandonedChangesRefs() throws Exception {
    createChangeAndAbandon();

    assertThat(getRefs(cloneProjectChangesRefs(admin))).hasSize(1);
  }

  @Test
  @GerritConfig(name = "git-refs-filter.hideRefs", value = "refs/heads/sandbox/")
  public void testUserWithHideRefsShouldNotSeeSandboxBranches() throws Exception {
    String sandboxPrefix = "refs/heads/sandbox/";
    setApiUser(admin);
    createBranch(new Branch.NameKey(project, "sandbox/foo"));

    assertThat(getRefs(cloneProjectRefs(admin, "+refs/heads/*:refs/heads/*"), sandboxPrefix))
        .isNotEmpty();
    assertThat(getRefs(cloneProjectRefs(user, "+refs/heads/*:refs/heads/*"), sandboxPrefix))
        .isEmpty();
  }

  @Test
  @GerritConfig(
      name = "git-refs-filter.hideRefs",
      values = {"refs/heads/sandbox/", "!refs/heads/sandbox/mine"})
  public void testUserWithHideRefsShouldSeeItsOwnSandboxBranch() throws Exception {
    String sandboxPrefix = "refs/heads/sandbox/";
    setApiUser(admin);
    createBranch(new Branch.NameKey(project, "sandbox/mine"));

    assertThat(getRefs(cloneProjectRefs(user, "+refs/heads/*:refs/heads/*"), sandboxPrefix))
        .isNotEmpty();
  }

  protected Stream<String> fetchAllRefs(TestAccount testAccount) throws Exception {
    DfsRepositoryDescription desc = new DfsRepositoryDescription("clone of " + project.get());

    FS fs = FS.detect();
    fs.setUserHome(null);

    InMemoryRepository dest =
        new InMemoryRepository.Builder().setRepositoryDescription(desc).setFS(fs).build();
    Config cfg = dest.getConfig();
    String uri = registerRepoConnection(project, testAccount);
    cfg.setString("remote", "origin", "url", uri);
    cfg.setString("remote", "origin", "fetch", "+refs/*:refs/remotes/origin/*");
    TestRepository<InMemoryRepository> testRepo = GitUtil.newTestRepository(dest);
    FetchResult result = testRepo.git().fetch().setRemote("origin").call();
    return result.getAdvertisedRefs().stream().map(Ref::getName);
  }

  protected List<Ref> getRefs(TestRepository<InMemoryRepository> repo, String prefix)
      throws IOException {
    return repo.getRepository().getRefDatabase().getRefsByPrefix(prefix);
  }
}
