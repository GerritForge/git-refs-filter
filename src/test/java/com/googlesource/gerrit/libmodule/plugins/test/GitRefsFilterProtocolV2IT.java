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

package com.googlesource.gerrit.libmodule.plugins.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.WaitUtil.waitUntil;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.gerrit.acceptance.AbstractGitDaemonTest;
import com.google.gerrit.acceptance.GitClientVersion;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Test;

@UseSsh
@UseLocalDisk
public class GitRefsFilterProtocolV2IT extends AbstractGitDaemonTest {
  private final String[] GIT_FETCH = new String[] {"git", "-c", "protocol.version=2", "fetch"};
  private final String[] GIT_INIT = new String[] {"git", "init"};

  private static final Duration TEST_PATIENCE_TIME = Duration.ofSeconds(1);

  @Inject private ProjectOperations projectOperations;
  @Inject private SitePaths sitePaths;
  @Inject private CanonicalWebUrl url;

  public static void assertGitClientVersion() throws Exception {
    // Minimum required git-core version that supports wire protocol v2 is 2.18.0
    GitClientVersion requiredGitVersion = new GitClientVersion(2, 18, 0);
    GitClientVersion actualGitVersion =
        new GitClientVersion(execute(ImmutableList.of("git", "version"), new File("/")));
    // If git client version cannot be updated, consider to skip this tests. Due to
    // an existing issue in bazel, JUnit assumption violation feature cannot be used.
    assertThat(actualGitVersion).isAtLeast(requiredGitVersion);
  }

  @Test
  public void testGitWireProtocolV2HidesAbandonedChange() throws Exception {
    assertGitClientVersion();

    Project.NameKey allRefsVisibleProject = Project.nameKey("all-refs-visible");
    gApi.projects().create(allRefsVisibleProject.get());

    setProjectPermissionReadAllRefs(allRefsVisibleProject);
    setHideClosedChangesRefs(SystemGroupBackend.ANONYMOUS_USERS.get());
    setProjectClosedChangesGraceTime(allRefsVisibleProject, Duration.ofSeconds(0));

    // Create new change and retrieve refs for the created patch set
    ChangeInput visibleChangeIn =
        new ChangeInput(allRefsVisibleProject.get(), "master", "Test public change");
    visibleChangeIn.newBranch = true;
    ChangeInfo changeInfo = gApi.changes().create(visibleChangeIn).info();
    int visibleChangeNumber = changeInfo._number;
    Change.Id changeId = Change.id(visibleChangeNumber);
    String visibleChangeNumberRef = RefNames.patchSetRef(PatchSet.id(changeId, 1));
    String patchSetSha1 = gApi.changes().id(visibleChangeNumber).get().currentRevision;
    String visibleChangeNumberWithSha1 = patchSetSha1 + " " + visibleChangeNumberRef;

    execute(ImmutableList.<String>builder().add(GIT_INIT).build(), ImmutableMap.of());
    String gitProtocolOutActiveChange = execFetch(allRefsVisibleProject, visibleChangeNumberRef);
    assertThat(gitProtocolOutActiveChange).contains(visibleChangeNumberWithSha1);

    gApi.changes().id(changeId.get()).abandon();

    waitUntil(
        () -> {
          try {
            return !execFetch(allRefsVisibleProject, visibleChangeNumberRef)
                .contains(visibleChangeNumberWithSha1);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        },
        TEST_PATIENCE_TIME);
  }

  private String execFetch(Project.NameKey project, String refs) throws Exception {
    String outAnonymousLsRemote =
        execute(
            ImmutableList.<String>builder()
                .add(GIT_FETCH)
                .add(url.get(null) + "/" + project.get())
                .add(refs)
                .build(),
            ImmutableMap.of("GIT_TRACE_PACKET", "1"));
    assertGitProtocolV2(outAnonymousLsRemote);
    return outAnonymousLsRemote;
  }

  private void assertGitProtocolV2(String outAnonymousLsRemote) {
    assertThat(outAnonymousLsRemote).contains("git< version 2");
  }

  private void setProjectPermissionReadAllRefs(Project.NameKey project) {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(SystemGroupBackend.ANONYMOUS_USERS))
        .update();
  }

  private String execute(ImmutableList<String> cmd, ImmutableMap<String, String> env)
      throws Exception {
    return execute(cmd, sitePaths.data_dir.toFile(), env);
  }

  private static String execute(ImmutableList<String> cmd, File dir) throws Exception {
    return execute(cmd, dir, ImmutableMap.of());
  }

  protected static String execute(
      ImmutableList<String> cmd, File dir, ImmutableMap<String, String> env) throws IOException {
    return execute(cmd, dir, env, null);
  }

  protected static String execute(
      ImmutableList<String> cmd,
      File dir,
      @Nullable ImmutableMap<String, String> env,
      @Nullable Path outputPath)
      throws IOException {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(dir);
    if (outputPath != null) {
      pb.redirectOutput(outputPath.toFile());
    } else {
      pb.redirectErrorStream(true);
    }
    pb.environment().putAll(env);
    Process p = pb.start();
    byte[] out;
    try (InputStream in = p.getInputStream()) {
      out = ByteStreams.toByteArray(in);
    } finally {
      p.getOutputStream().close();
    }

    try {
      p.waitFor();
    } catch (InterruptedException e) {
      InterruptedIOException iioe =
          new InterruptedIOException(
              "interrupted waiting for: " + Joiner.on(' ').join(pb.command()));
      iioe.initCause(e);
      throw iioe;
    }

    String result = new String(out, UTF_8);
    return result.trim();
  }
}
