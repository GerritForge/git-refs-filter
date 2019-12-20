package com.googlesource.gerrit.modules.gitrefsfilter;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GitRefsFilterConfig {

    private static final String PLUGIN_NAME = "git-refs-filter";
    private static final Logger log = LoggerFactory.getLogger(GitRefsFilterConfig.class);
    private final PluginConfigFactory cfgFactory;

    @Inject
    GitRefsFilterConfig(PluginConfigFactory cfgFactory) {
        this.cfgFactory = cfgFactory;
    }

    PluginConfig getPluginConfigFor(Project.NameKey projectNameKey) {
        try {
            return cfgFactory.getFromProjectConfigWithInheritance(projectNameKey, PLUGIN_NAME);
        } catch (NoSuchProjectException e) {
            log.warn(
                    "Unable to get project configuration for {}: project '{}' not found ",
                    PLUGIN_NAME,
                    projectNameKey.get(),
                    e);
            return new PluginConfig(PLUGIN_NAME, new Config());
        }
    }
}
