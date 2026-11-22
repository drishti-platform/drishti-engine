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

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The ready-made recorder behind {@link Engine#stats}: lock-free counters (one atomic add per event), latency in log-scale buckets
 * (about 3% resolution), and a recent window of one-minute slots. An engine creates one at {@code init} with the context's clock,
 * records at the points of a request, and returns {@link #snapshot} from {@code stats()}. No thread, no I/O.
 */
public final class EngineMeter {

    /** How a request ended. */
    public enum Outcome { ANSWERED, REFUSED, FAILED, CANCELLED }

    private static final int BUCKETS = 64 * 16;                       // 16 sub-buckets per power of two of the nanoseconds
    private static final int SLOTS = (int) EngineStats.RECENT_SPAN.toMinutes();
    private static final long SLOT_NANOS = 60_000_000_000L;

    /** Counters of one window; the same shape for since-start and for each slot. */
    private static final class Cells {
        final AtomicLong asked = new AtomicLong(), answered = new AtomicLong(), refused = new AtomicLong(), failed = new AtomicLong(),
                cancelled = new AtomicLong(), rowsIn = new AtomicLong(), rowsOut = new AtomicLong(), bytesIn = new AtomicLong(),
                bytesOut = new AtomicLong(), hits = new AtomicLong(), misses = new AtomicLong();
        final AtomicLongArray latency = new AtomicLongArray(BUCKETS);
    }

    private final Clock clock;
    private final Cells total = new Cells();
    private final Cells[] slots = new Cells[SLOTS];
    private final long[] slotMinute = new long[SLOTS];                 // guarded by slots
    private final AtomicInteger sessions = new AtomicInteger();
    private final AtomicInteger queue = new AtomicInteger();
    private volatile long lastActivityMillis = Long.MIN_VALUE;

    public EngineMeter(Clock clock) {
        this.clock = clock;
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = new Cells();
            slotMinute[i] = Long.MIN_VALUE;
        }
    }

    /** A request reached the engine. */
    public void asked() {
        total.asked.incrementAndGet();
        slot().asked.incrementAndGet();
    }

    /** A request finished after {@code nanos}, having read and returned the given sizes (0 when unknown). */
    public void finished(Outcome outcome, long nanos, long rowsIn, long rowsOut, long bytesIn, long bytesOut) {
        Cells s = slot();
        for (Cells c : new Cells[] {total, s}) {
            switch (outcome) {
                case ANSWERED -> c.answered.incrementAndGet();
                case REFUSED -> c.refused.incrementAndGet();
                case FAILED -> c.failed.incrementAndGet();
                case CANCELLED -> c.cancelled.incrementAndGet();
            }
            c.rowsIn.addAndGet(rowsIn);
            c.rowsOut.addAndGet(rowsOut);
            c.bytesIn.addAndGet(bytesIn);
            c.bytesOut.addAndGet(bytesOut);
            c.latency.incrementAndGet(bucket(nanos));
        }
        lastActivityMillis = clock.millis();
    }

    /** What a request does, for {@link #measure}. */
    @FunctionalInterface
    public interface Call {
        Answer run() throws EngineException;
    }

    /**
     * Runs one request and records it: asked, its outcome by the exception's kind, its latency (the nanoTime clock: a duration, not a
     * date), the rows and bytes of the tables it returned (bytes: 0 unless the engine records them itself).
     */
    public Answer measure(Call call, long rowsIn, long bytesIn) throws EngineException {
        asked();
        long t0 = System.nanoTime();
        try {
            Answer a = call.run();
            long rows = 0;
            for (Table t : a.tables()) {
                rows += t.rows();
            }
            finished(Outcome.ANSWERED, System.nanoTime() - t0, rowsIn, rows, bytesIn, 0);
            return a;
        } catch (EngineException e) {
            Outcome o = switch (e.kind()) {
                case CANCELLED, TIMED_OUT -> Outcome.CANCELLED;
                case REFUSED, NOT_PERMITTED, LIMIT -> Outcome.REFUSED;
                default -> Outcome.FAILED;
            };
            finished(o, System.nanoTime() - t0, 0, 0, 0, 0);
            throw e;
        } catch (RuntimeException | Error e) {
            finished(Outcome.FAILED, System.nanoTime() - t0, 0, 0, 0, 0);
            throw e;
        }
    }

    public void cacheHit() {
        total.hits.incrementAndGet();
        slot().hits.incrementAndGet();
    }

    public void cacheMiss() {
        total.misses.incrementAndGet();
        slot().misses.incrementAndGet();
    }

    public void sessionOpened() {
        sessions.incrementAndGet();
    }

    public void sessionClosed() {
        sessions.updateAndGet(n -> Math.max(0, n - 1));
    }

    /** The engine's own queue, if it has one. */
    public void queued(int delta) {
        queue.addAndGet(delta);
    }

    /** A consistent-enough snapshot; counters read separately, so {@code asked} is read last and never lags the finished ones. */
    public EngineStats snapshot() {
        long minute = minute();
        EngineStats.Counters recent = EngineStats.Counters.ZERO;
        Cells sum = new Cells();
        synchronized (slots) {
            for (int i = 0; i < SLOTS; i++) {
                if (slotMinute[i] != Long.MIN_VALUE && minute - slotMinute[i] < SLOTS) {
                    add(sum, slots[i]);
                }
            }
        }
        recent = counters(sum);
        return new EngineStats(counters(total), recent, sessions.get(), queue.get(),
                lastActivityMillis == Long.MIN_VALUE ? null : Instant.ofEpochMilli(lastActivityMillis), clock.instant());
    }

    private long minute() {
        return clock.millis() / (SLOT_NANOS / 1_000_000L);
    }

    private Cells slot() {
        long minute = minute();
        int i = (int) Math.floorMod(minute, (long) SLOTS);
        synchronized (slots) {
            if (slotMinute[i] != minute) {
                slots[i] = new Cells();
                slotMinute[i] = minute;
            }
            return slots[i];
        }
    }

    private static void add(Cells to, Cells from) {
        to.asked.addAndGet(from.asked.get());
        to.answered.addAndGet(from.answered.get());
        to.refused.addAndGet(from.refused.get());
        to.failed.addAndGet(from.failed.get());
        to.cancelled.addAndGet(from.cancelled.get());
        to.rowsIn.addAndGet(from.rowsIn.get());
        to.rowsOut.addAndGet(from.rowsOut.get());
        to.bytesIn.addAndGet(from.bytesIn.get());
        to.bytesOut.addAndGet(from.bytesOut.get());
        to.hits.addAndGet(from.hits.get());
        to.misses.addAndGet(from.misses.get());
        for (int b = 0; b < BUCKETS; b++) {
            to.latency.addAndGet(b, from.latency.get(b));
        }
    }

    private static EngineStats.Counters counters(Cells c) {
        long answered = c.answered.get(), refused = c.refused.get(), failed = c.failed.get(), cancelled = c.cancelled.get();
        long finished = answered + refused + failed + cancelled;       // read before asked: asked >= finished holds
        long[] hist = new long[BUCKETS];
        long n = 0;
        for (int b = 0; b < BUCKETS; b++) {
            hist[b] = c.latency.get(b);
            n += hist[b];
        }
        long asked = Math.max(c.asked.get(), finished);
        return new EngineStats.Counters(asked, answered, refused, failed, cancelled, c.rowsIn.get(), c.rowsOut.get(), c.bytesIn.get(),
                c.bytesOut.get(), c.hits.get(), c.misses.get(), quantile(hist, n, 0.50), quantile(hist, n, 0.95), quantile(hist, n, 0.99));
    }

    private static int bucket(long nanos) {
        long v = Math.max(nanos, 1);
        int exp = 63 - Long.numberOfLeadingZeros(v);
        int sub = exp >= 4 ? (int) ((v >>> (exp - 4)) & 15) : (int) ((v << (4 - exp)) & 15);
        return Math.min(exp * 16 + sub, BUCKETS - 1);
    }

    private static long upper(int bucket) {
        int exp = bucket / 16;
        int sub = bucket % 16;
        long base = 1L << exp;
        return base + (exp >= 4 ? (base >>> 4) * (sub + 1) : ((sub + 1) >>> (4 - exp)));
    }

    private static long quantile(long[] hist, long n, double q) {
        if (n == 0) {
            return 0;
        }
        long rank = (long) Math.ceil(q * n);
        long seen = 0;
        for (int b = 0; b < BUCKETS; b++) {
            seen += hist[b];
            if (seen >= rank) {
                return upper(b);
            }
        }
        return upper(BUCKETS - 1);
    }
}
