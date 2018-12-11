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

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.DefaultPermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendCondition;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Set;

public class RefsFilterPermissionBackend extends PermissionBackend {
  private final DefaultPermissionBackend defaultBackend;
  private final WithUserWrapper.Factory filteredRefsUserFactory;
  private final Provider<CurrentUser> currentUserProvider;

  @Inject
  RefsFilterPermissionBackend(
      DefaultPermissionBackend defaultBackend,
      WithUserWrapper.Factory filteredRefsUserFactory,
      Provider<CurrentUser> currentUserProvider) {
    this.defaultBackend = defaultBackend;
    this.filteredRefsUserFactory = filteredRefsUserFactory;
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public WithUser user(CurrentUser user) {
    return filteredRefsUserFactory.get(defaultBackend.user(user));
  }

  @Override
  public WithUser currentUser() {
    return user(currentUserProvider.get());
  }

  @Override
  public WithUser absentUser(Account.Id id) {
    return defaultBackend.absentUser(id);
  }

  @Override
  public boolean usesDefaultCapabilities() {
    return defaultBackend.usesDefaultCapabilities();
  }

  @Override
  public void checkUsesDefaultCapabilities() throws ResourceNotFoundException {
    defaultBackend.checkUsesDefaultCapabilities();
  }

  @Override
  public void bulkEvaluateTest(Set<PermissionBackendCondition> conds) {
    defaultBackend.bulkEvaluateTest(conds);
  }
}
