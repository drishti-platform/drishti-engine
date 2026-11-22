/*
 * Drishti Engine · engines by contract, proven with a JDK only.
 *
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>.
 * All rights reserved.
 *
 * PROPRIETARY AND CONFIDENTIAL.
 *
 * This file is the confidential and proprietary property of Ashutosh Sinha.
 * Unauthorised copying, use, modification, distribution or disclosure of this
 * file, via any medium, is strictly prohibited except with the express prior
 * written permission of the copyright holder.
 *
 * See the LICENSE file in the root of this repository for the full terms.
 */
package com.ash.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A typed snapshot of an engine instance's counters (specification 7.5): two views, {@code sinceStart} (monotonic; since
 * {@code start}, kept across suspend and resume) and {@code recent} (the last {@link #RECENT_SPAN}, moving). Built from counters the
 * engine already keeps; reading it takes no lock a request holds and does no I/O. {@link EngineMeter} is the ready-made recorder.
 *
 * @param sinceStart  counters since {@code start}
 * @param recent      counters over the recent window
 * @param sessionsOpen sessions open now (a gauge)
 * @param queueDepth  requests waiting inside the engine now (a gauge; 0 when it queues none)
 * @param lastActivity the engine's clock at the last request that finished; null when none has
 * @param takenAt     the engine's clock when the snapshot was formed
 */
public record EngineStats(Counters sinceStart, Counters recent, int sessionsOpen, int queueDepth, Instant lastActivity, Instant takenAt) {

    /** The width of the recent window. */
    public static final Duration RECENT_SPAN = Duration.ofMinutes(5);

    /**
     * One view of the counters. Invariant: {@code asked >= answered + refused + failed + cancelled} (the difference is the requests in
     * flight). Latencies are of finished requests, in nanoseconds (0 when there are none).
     *
     * @param asked     requests that reached the engine
     * @param answered  finished with an Answer (COMPLETE, PARTIAL, NOT_FOUND)
     * @param refused   finished as REFUSED, NOT_PERMITTED or LIMIT
     * @param failed    finished as FAILED
     * @param cancelled finished as CANCELLED or TIMED_OUT
     * @param rowsIn    input rows the engine read to answer
     * @param rowsOut   rows in the answers it gave
     * @param bytesIn   bytes read from inputs
     * @param bytesOut  bytes in the answers
     * @param cacheHits  the engine's own cache hits (not the host's answer cache)
     * @param cacheMisses the engine's own cache misses
     * @param p50Nanos  median latency
     * @param p95Nanos  95th percentile latency
     * @param p99Nanos  99th percentile latency
     */
    public record Counters(long asked, long answered, long refused, long failed, long cancelled, long rowsIn, long rowsOut, long bytesIn,
            long bytesOut, long cacheHits, long cacheMisses, long p50Nanos, long p95Nanos, long p99Nanos) {

        public static final Counters ZERO = new Counters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        /** Requests that finished. */
        public long finished() {
            return answered + refused + failed + cancelled;
        }

        /** Requests running now (asked, not finished). */
        public long inFlight() {
            return asked - finished();
        }
    }

    public EngineStats {
        Objects.requireNonNull(sinceStart, "sinceStart");
        Objects.requireNonNull(recent, "recent");
        Objects.requireNonNull(takenAt, "takenAt");
    }

    /** All zero: an engine that has served nothing. */
    public static EngineStats empty(Instant at) {
        return new EngineStats(Counters.ZERO, Counters.ZERO, 0, 0, null, at);
    }
}
