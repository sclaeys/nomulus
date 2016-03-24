// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.domain.registry.rde;

import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.RegistryCursor;
import com.google.domain.registry.model.registry.RegistryCursor.CursorType;
import com.google.domain.registry.model.server.Lock;
import com.google.domain.registry.request.HttpException.NoContentException;
import com.google.domain.registry.request.HttpException.ServiceUnavailableException;
import com.google.domain.registry.request.Parameter;
import com.google.domain.registry.request.RequestParameters;
import com.google.domain.registry.util.Clock;
import com.google.domain.registry.util.FormattingLogger;

import com.googlecode.objectify.VoidWork;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.concurrent.Callable;

import javax.inject.Inject;

/**
 * Runner applying guaranteed reliability to an {@link EscrowTask}.
 *
 * <p>This class implements the <i>Locking Rolling Cursor</i> pattern, which solves the problem of
 * how to reliably execute App Engine tasks which can't be made idempotent.
 *
 * <p>{@link Lock} is used to ensure only one task executes at a time for a given
 * {@code LockedCursorTask} subclass + TLD combination. This is necessary because App Engine tasks
 * might double-execute. Normally tasks solve this by being idempotent, but that's not possible for
 * RDE, which writes to a GCS filename with a deterministic name. So the datastore is used to to
 * guarantee isolation. If we can't acquire the lock, it means the task is already running, so
 * {@link NoContentException} is thrown to cancel the task.
 *
 * <p>The specific date for which the deposit is generated depends on the current position of the
 * {@link RegistryCursor}. If the cursor is set to tomorrow, we do nothing and return 204 No
 * Content. If the cursor is set to today, then we create a deposit for today and advance the
 * cursor. If the cursor is set to yesterday or earlier, then we create a deposit for that date,
 * advance the cursor, but we <i>do not</i> make any attempt to catch the cursor up to the current
 * time. Therefore <b>you must</b> set the cron interval to something less than the desired
 * interval, so the cursor can catch up. For example, if the task is supposed to run daily, you
 * should configure cron to execute it every twelve hours, or possibly less.
 */
class EscrowTaskRunner {

  /** Callback interface for objects managed by {@link EscrowTaskRunner}. */
  public interface EscrowTask {

    /**
     * Performs task logic while the lock is held.
     *
     * @param watermark the logical time for a point-in-time view of datastore
     */
    abstract void runWithLock(DateTime watermark) throws Exception;
  }

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject Clock clock;
  @Inject @Parameter(RequestParameters.PARAM_TLD) String tld;
  @Inject EscrowTaskRunner() {}

  /**
   * Acquires lock, checks cursor, invokes {@code task}, and advances cursor.
   *
   * @param task the task to run
   * @param registry the {@link Registry} that we are performing escrow for
   * @param timeout time when we assume failure, kill the task (and instance) and release the lock
   * @param cursorType the cursor to advance on success, indicating the next required runtime
   * @param interval how far to advance the cursor (e.g. a day for RDE, a week for BRDA)
   */
  void lockRunAndRollForward(
      final EscrowTask task,
      final Registry registry,
      Duration timeout,
      final CursorType cursorType,
      final Duration interval) {
    Callable<Void> lockRunner = new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        logger.info("tld=" + registry.getTld());
        DateTime startOfToday = clock.nowUtc().withTimeAtStartOfDay();
        final DateTime nextRequiredRun = RegistryCursor.load(registry, cursorType).or(startOfToday);
        if (nextRequiredRun.isAfter(startOfToday)) {
          throw new NoContentException("Already completed");
        }
        logger.info("cursor=" + nextRequiredRun);
        task.runWithLock(nextRequiredRun);
        ofy().transact(new VoidWork() {
          @Override
          public void vrun() {
            RegistryCursor.save(registry, cursorType, nextRequiredRun.plus(interval));
          }});
        return null;
      }};
    String lockName = String.format("%s %s", task.getClass().getSimpleName(), registry.getTld());
    if (!Lock.executeWithLocks(lockRunner, null, tld, timeout, lockName)) {
      // This will happen if either: a) the task is double-executed; b) the task takes a long time
      // to run and the retry task got executed while the first one is still running. In both
      // situations the safest thing to do is to just return 503 so the task gets retried later.
      throw new ServiceUnavailableException("Lock in use: " + lockName);
    }
  }
}
