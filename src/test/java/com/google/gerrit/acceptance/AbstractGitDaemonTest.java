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

package com.google.gerrit.acceptance;

import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.googlesource.gerrit.modules.gitrefsfilter.FilterRefsCapability;
import com.googlesource.gerrit.modules.gitrefsfilter.FilterRefsConfig;
import com.googlesource.gerrit.modules.gitrefsfilter.RefsFilterModule;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.util.FS;

public abstract class AbstractGitDaemonTest extends AbstractDaemonTest {
  private static final String REFS_CHANGES = "+refs/changes/*:refs/remotes/origin/*";

  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  @Override
  public Module createModule() {
    return new RefsFilterModule();
  }

  protected int createChangeAndAbandon() throws Exception, RestApiException {
    requestScopeOperations.setApiUser(admin.id());
    createChange();
    int changeNum = changeNumOfRef(getChangesRefsAs(admin).get(0));
    gApi.changes().id(changeNum).abandon();
    return changeNum;
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

    setHideClosedChangesRefs(groupId);
  }

  protected void setHideClosedChangesRefs(String groupId) {
    projectOperations
        .allProjectsForUpdate()
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
    return cloneProjectRefs(testAccount, REFS_CHANGES);
  }

  protected TestRepository<InMemoryRepository> cloneProjectRefs(
      TestAccount testAccount, String refsSpec) throws Exception {
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
    String uri = registerAndGetRepoConnection(project, testAccount);
    cfg.setString("remote", "origin", "url", uri);
    cfg.setString("remote", "origin", "fetch", refsSpec);
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

  protected void setProjectClosedChangesGraceTime(Project.NameKey project, Duration graceTime)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig projectConfig = projectConfigFactory.create(project);
      projectConfig.load(md);
      projectConfig.updatePluginConfig(
          "gerrit",
          cfg ->
              cfg.setLong(
                  FilterRefsConfig.PROJECT_CONFIG_CLOSED_CHANGES_GRACE_TIME_SEC,
                  graceTime.toSeconds()));
      projectConfig.commit(md);
      projectCache.evict(project);
    }
  }

  protected String registerAndGetRepoConnection(Project.NameKey p, TestAccount testAccount) throws Exception {
    return registerRepoConnection(p, testAccount);
  }
}