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

public class ChangesTsCache {
  public static final String CHANGES_CACHE_TS = "changes_ts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CHANGES_CACHE_TS, ChangeCacheKey.class, new TypeLiteral<Long>() {})
            .loader(Loader.class);
      }
    };
  }

  @Singleton
  static class Loader extends CacheLoader<ChangeCacheKey, Long> {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private final ChangeNotes.Factory changeNotesFactory;

    @Inject
    Loader(ChangeNotes.Factory changeNotesFactory) {
      this.changeNotesFactory = changeNotesFactory;
    }

    @Override
    public Long load(ChangeCacheKey key) throws Exception {
      try {
        return changeNotesFactory
            .createChecked(key.repo(), key.project(), key.changeId(), key.changeRevision())
            .getChange()
            .getLastUpdatedOn()
            .getTime();
      } catch (NoSuchChangeException e) {
        logger.atFine().withCause(e).log(
            "Change %d does not exist: returning zero epoch", key.changeId().get());
        return 0L;
      }
    }
  }
}
