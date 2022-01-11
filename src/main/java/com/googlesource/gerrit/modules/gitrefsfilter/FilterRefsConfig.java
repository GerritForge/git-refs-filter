// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;

public class FilterRefsConfig {
  public static final String SECTION_GIT_REFS_FILTER = "git-refs-filter";
  public static final String KEY_HIDE_REFS = "hideRefs";
  public static final String PROJECT_CONFIG_CLOSED_CHANGE_GRACE_TIME_MSEC =
      "gitRefFilterClosedChangeGraceTimeMsec";

  public static final long DEFAULT_CLOSED_CHANGE_GRACE_TIME_MSEC =
      TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS);

  private final List<String> hideRefs;
  private final List<String> showRefs;
  private PluginConfigFactory cfgFactory;

  @Inject
  public FilterRefsConfig(@GerritServerConfig Config gerritConfig, PluginConfigFactory cfgFactory) {

    this.cfgFactory = cfgFactory;
    List<String> hideRefsConfig =
        Arrays.asList(gerritConfig.getStringList(SECTION_GIT_REFS_FILTER, null, KEY_HIDE_REFS));

    hideRefs =
        ImmutableList.copyOf(
            hideRefsConfig.stream()
                .filter(s -> !s.startsWith("!"))
                .map(String::trim)
                .collect(Collectors.toList()));
    showRefs =
        ImmutableList.copyOf(
            hideRefsConfig.stream()
                .filter(s -> s.startsWith("!"))
                .map(s -> s.substring(1))
                .map(String::trim)
                .collect(Collectors.toList()));
  }

  public boolean isRefToShow(Ref ref) {
    String refName = ref.getName();

    for (String refToShow : showRefs) {
      if (refName.startsWith(refToShow)) {
        return true;
      }
    }

    for (String refToHide : hideRefs) {
      if (refName.startsWith(refToHide)) {
        return false;
      }
    }

    return true;
  }

  public long getClosedChangeGraceTimeMsec(Project.NameKey projectKey)
      throws NoSuchProjectException {
    return cfgFactory
        .getFromProjectConfigWithInheritance(projectKey, "gerrit")
        .getLong(
            PROJECT_CONFIG_CLOSED_CHANGE_GRACE_TIME_MSEC, DEFAULT_CLOSED_CHANGE_GRACE_TIME_MSEC);
  }
}
