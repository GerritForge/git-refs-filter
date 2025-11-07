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

package com.gerritforge.gerrit.modules.gitrefsfilter;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
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
  public WithUser user(
      CurrentUser user, IdentifiedUser.ImpersonationPermissionMode permissionMode) {
    return defaultBackend.user(user, permissionMode);
  }

  @Override
  public WithUser exactUser(CurrentUser user) {
    return defaultBackend.exactUser(user);
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
