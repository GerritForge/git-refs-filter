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

import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import com.googlesource.gerrit.modules.gitrefsfilter.FilterRefsCapability;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.util.FS;

abstract class AbstractGitDaemonTest extends AbstractDaemonTest {
  private static final String REFS_CHANGES = "+refs/changes/*:refs/remotes/origin/*";

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  protected void createChangeAndAbandon() throws Exception, RestApiException {
    requestScopeOperations.setApiUser(admin.id());
    createChange();
    int changeNum = changeNumOfRef(getChangesRefsAs(admin).get(0));
    gApi.changes().id(changeNum).abandon();
  }

  protected void createFilteredRefsGroup() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    String group = name("filtered-refs-group");
    GroupInput in = new GroupInput();
    in.name = group;
    GroupApi groupApi = gApi.groups().create(in);
    groupApi.addMembers(user.username());

    requestScopeOperations.setApiUser(user.id());
    groupApi.removeMembers(admin.username());
    String groupId = groupApi.detail().id;

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(
            allowCapability("gerrit-" + FilterRefsCapability.HIDE_CLOSED_CHANGES_REFS)
                .group(AccountGroup.UUID.parse(groupId)))
        .update();
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

  protected int changeNumOfRef(Ref ref) {
    /*
     * refName is refs/remotes/origin/<NN>/<change-num>
     */
    return Integer.parseInt(ref.getName().split("/")[4]);
  }
}
