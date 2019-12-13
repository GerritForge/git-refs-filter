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

import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackend.ForProject;
import com.google.gerrit.server.permissions.PermissionBackend.WithUser;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.Set;

public class WithUserWrapper extends WithUser {
  private final FilterRefsPermission filterRefsPermission;
  private final WithUser defaultWithUser;
  private final ForProjectWrapper.Factory forProjectFactory;

  public interface Factory {
    WithUserWrapper get(WithUser defaultWithUser);
  }

  @Inject
  WithUserWrapper(
      FilterRefsPermission filterRefsPermission,
      ForProjectWrapper.Factory forProjectFactory,
      @Assisted WithUser defaultWithUser) {
    this.filterRefsPermission = filterRefsPermission;
    this.defaultWithUser = defaultWithUser;
    this.forProjectFactory = forProjectFactory;
  }

  @Override
  public ForProject project(NameKey project) {
    ForProject defaultWithProject = defaultWithUser.project(project);
    if (defaultWithUser.testOrFalse(filterRefsPermission)) {
      return forProjectFactory.get(defaultWithProject, project);
    }
    return defaultWithProject;
  }

  @Override
  public void check(GlobalOrPluginPermission perm)
      throws AuthException, PermissionBackendException {
    defaultWithUser.check(perm);
  }

  @Override
  public <T extends GlobalOrPluginPermission> Set<T> test(Collection<T> permSet)
      throws PermissionBackendException {
    return defaultWithUser.test(permSet);
  }

  @Override
  public BooleanCondition testCond(GlobalOrPluginPermission perm) {
    return defaultWithUser.testCond(perm);
  }
}
