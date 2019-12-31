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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.googlesource.gerrit.modules.gitrefsfilter.FilterRefsCapability;
import com.googlesource.gerrit.modules.gitrefsfilter.GitRefsFilterConfigFactory;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.util.FS;

abstract class AbstractGitDaemonTest extends AbstractDaemonTest {
  private static final String REFS_CHANGES = "+refs/changes/*:refs/remotes/origin/*";

  protected static final String CHANGE_REF = "refs/remotes/origin/01/1/1";
  protected static final String CHANGE_REF_META = "refs/remotes/origin/01/1/meta";

  protected void createChangeAndAbandon() throws Exception, RestApiException {
    setApiUser(admin);
    createChange();
    int changeNum = changeNumOfRef(getChangesRefsAs(admin).get(0));
    gApi.changes().id(changeNum).abandon();
  }

  protected void createFilteredRefsGroup() throws Exception {
    setApiUser(admin);
    String group = name("filtered-refs-group");
    GroupInput in = new GroupInput();
    in.name = group;
    GroupApi groupApi = gApi.groups().create(in);
    groupApi.addMembers(user.username);

    setApiUser(user);
    groupApi.removeMembers(admin.username);
    String groupId = groupApi.detail().id;

    allowGlobalCapabilities(
        AccountGroup.UUID.parse(groupId),
        "gerrit-" + FilterRefsCapability.HIDE_CLOSED_CHANGES_REFS);
  }

  protected void filterOnlyClosedChangesOlderThan(String timeUnit) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .getPluginConfig(GitRefsFilterConfigFactory.PLUGIN_NAME)
          .setString(
              GitRefsFilterConfigFactory.HIDE_ONLY_CLOSED_CHANGES_OLDER_THAN_FIELD, timeUnit);
      u.save();
    }
  }

  protected List<Ref> getChangesRefsAs(TestAccount testAccount) throws Exception {
    return getRefs(cloneProjectChangesRefs(testAccount));
  }

  protected TestRepository<InMemoryRepository> cloneProjectChangesRefs(TestAccount testAccount)
      throws Exception {
    DfsRepositoryDescription desc = new DfsRepositoryDescription("clone of " + project.get());

    FS fs = FS.detect();

    // Avoid leaking user state into our tests.
    fs.setUserHome(null);

    InMemoryRepository dest =
        new InMemoryRepository.Builder()
            .setRepositoryDescription(desc)
            // SshTransport depends on a real FS to read ~/.ssh/config, but
            // InMemoryRepository by default uses a null FS.
            // TODO(dborowitz): Remove when we no longer depend on SSH.
            .setFS(fs)
            .build();
    Config cfg = dest.getConfig();
    String uri = registerRepoConnection(project, testAccount);
    cfg.setString("remote", "origin", "url", uri);
    cfg.setString("remote", "origin", "fetch", REFS_CHANGES);
    TestRepository<InMemoryRepository> testRepo = GitUtil.newTestRepository(dest);
    FetchResult result = testRepo.git().fetch().setRemote("origin").call();
    String originMaster = "refs/remotes/origin/master";
    if (result.getTrackingRefUpdate(originMaster) != null) {
      testRepo.reset(originMaster);
    }
    return testRepo;
  }

  protected List<Ref> getRefs(TestRepository<InMemoryRepository> repo) throws IOException {
    return repo.getRepository().getRefDatabase().getRefs();
  }

  protected List<String> getRefsString(TestAccount account) throws Exception {
    return getRefs(cloneProjectChangesRefs(account)).stream().map(Ref::getName).collect(Collectors.toList());
  }

  protected int changeNumOfRef(Ref ref) {
    /*
     * refName is refs/remotes/origin/<NN>/<change-num>
     */
    return Integer.parseInt(ref.getName().split("/")[4]);
  }
}
