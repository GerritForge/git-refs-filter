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

package com.googlesource.gerrit.modules.gitrefsfilter;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class RefsFilterModule extends AbstractModule {

  @Override
  protected void configure() {
    install(
        new FactoryModuleBuilder()
            .implement(WithUserWrapper.class, WithUserWrapper.class)
            .build(WithUserWrapper.Factory.class));

    install(
        new FactoryModuleBuilder()
            .implement(ForProjectWrapper.class, ForProjectWrapper.class)
            .build(ForProjectWrapper.Factory.class));

    bind(PermissionBackend.class).to(RefsFilterPermissionBackend.class).in(Scopes.SINGLETON);

    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(FilterRefsCapability.HIDE_CLOSED_CHANGES_REFS))
        .to(FilterRefsCapability.class)
        .in(Scopes.SINGLETON);

    install(ChangeOpenCache.module());
  }
}
