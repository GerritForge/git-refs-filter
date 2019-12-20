// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GitRefsFilterConfigFactory {

  public static final String PLUGIN_NAME = "git-refs-filter";
  public static final String HIDE_ONLY_CLOSED_CHANGES_OLDER_THAN_FIELD =
      "hide-only-closed-changes-older-than";
  private static final Logger log = LoggerFactory.getLogger(GitRefsFilterConfigFactory.class);
  private final PluginConfigFactory cfgFactory;

  @Inject
  GitRefsFilterConfigFactory(PluginConfigFactory cfgFactory) {
    this.cfgFactory = cfgFactory;
  }

  GitRefsFilterConfig getConfigForProject(Project.NameKey projectNameKey) {
    try {
      PluginConfig pluginConfig =
          cfgFactory.getFromProjectConfigWithInheritance(projectNameKey, PLUGIN_NAME);
      Optional<Timestamp> onlyClosedChangesTimestamp =
          extractHideOnlyClosedChangesThreshold(pluginConfig);

      if (onlyClosedChangesTimestamp.isPresent()) {
        log.warn(
            "Only closed changes older than {} will be filtered out for project {}",
            onlyClosedChangesTimestamp.get().toString(),
            projectNameKey.get());
      }

      return new GitRefsFilterConfig(onlyClosedChangesTimestamp);

    } catch (NoSuchProjectException e) {
      log.warn(
          "Unable to get project configuration for {}: project '{}' not found. Fall back to defaults: hide all closed changes.",
          PLUGIN_NAME,
          projectNameKey.get(),
          e);
      return new GitRefsFilterConfig(Optional.empty());
    }
  }

  private Optional<Timestamp> extractHideOnlyClosedChangesThreshold(PluginConfig pluginConfig) {
    return Optional.ofNullable(pluginConfig.getString(HIDE_ONLY_CLOSED_CHANGES_OLDER_THAN_FIELD))
        .map(timeString -> ConfigUtil.getTimeUnit(timeString, 0, TimeUnit.SECONDS))
        .map(LocalDateTime.now()::minusSeconds)
        .map(Timestamp::valueOf);
  }
}
