// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.concurrent.TimeUnit;

public class GitRefsFilterConfig {
  interface Factory {
    GitRefsFilterConfig create(Project.NameKey projectNameKey);
  }

  // This is not injected via @pluginName, because git-refs-filter is a lib, not a plugin, so the
  // annotation is not available
  public static final String PLUGIN_NAME = "git-refs-filter";
  public static final String HIDE_CLOSED_CHANGES_AFTER_FIELD = "hideClosedChangesAfter";
  private static final long HIDE_CLOSED_CHANGES_AFTER_SECS_DEFAULT = 0L;

  private long hideClosedChangesAfterSecs;

  @AssistedInject
  GitRefsFilterConfig(PluginConfigFactory cfgFactory, @Assisted Project.NameKey projectNameKey)
      throws NoSuchProjectException {
    PluginConfig pluginConfig =
        cfgFactory.getFromProjectConfigWithInheritance(projectNameKey, PLUGIN_NAME);
    hideClosedChangesAfterSecs = extractClosedChangesThresholdSecs(pluginConfig);
  }

  long hideClosedChangesAfterSecs() {
    return hideClosedChangesAfterSecs;
  }

  private static long extractClosedChangesThresholdSecs(PluginConfig pluginConfig) {
    return ConfigUtil.getTimeUnit(
        Strings.nullToEmpty(pluginConfig.getString(HIDE_CLOSED_CHANGES_AFTER_FIELD)),
        HIDE_CLOSED_CHANGES_AFTER_SECS_DEFAULT,
        TimeUnit.SECONDS);
  }
}
