// Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.cache.CacheLoader;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

public class OpenChangesCache {
  public static final String OPEN_CHANGES_CACHE = "open_changes";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(OPEN_CHANGES_CACHE, ChangeCacheKey.class, new TypeLiteral<Boolean>() {})
            .loader(Loader.class);
      }
    };
  }

  @Singleton
  static class Loader extends CacheLoader<ChangeCacheKey, Boolean> {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private final ChangeNotes.Factory changeNotesFactory;

    @Inject
    Loader(ChangeNotes.Factory changeNotesFactory) {
      this.changeNotesFactory = changeNotesFactory;
    }

    @Override
    public Boolean load(ChangeCacheKey key) throws Exception {
      try {
        ChangeNotes changeNotes =
            changeNotesFactory.createChecked(
                key.dbProvider().get(),
                key.repo(),
                key.project(),
                key.changeId(),
                key.changeRevision());
        return changeNotes.getChange().getStatus().isOpen();
      } catch (NoSuchChangeException e) {
        logger.atFine().withCause(e).log(
            "Change %d does not exist: hiding from the advertised refs", key.changeId().get());
        return false;
      }
    }
  }
}
