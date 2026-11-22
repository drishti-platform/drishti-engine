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
package com.ash.engine.host;

import com.ash.engine.EngineException;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.MemoryBudget;
import java.util.concurrent.atomic.AtomicLong;

/** A host's memory budget: reservations up to a limit; over it, {@code ENG-5002} in words and the engine stays usable. */
public final class Budget implements MemoryBudget {

    private volatile long limit;
    private final AtomicLong used = new AtomicLong();
    private final AtomicLong peak = new AtomicLong();
    private final AtomicLong refusals = new AtomicLong();

    public Budget(long limitBytes) {
        this.limit = limitBytes;
    }

    @Override
    public Reservation reserve(long bytes, String what) throws EngineException {
        take(bytes, what);
        return new Held(bytes, what);
    }

    private void take(long bytes, String what) throws EngineException {
        if (bytes < 0) {
            throw new IllegalArgumentException("a reservation of " + bytes + " bytes");
        }
        while (true) {
            long u = used.get();
            if (u + bytes > limit) {
                refusals.incrementAndGet();
                throw new EngineException("ENG-5002", Kind.LIMIT, "the memory budget cannot give " + mb(bytes) + " for " + what + ": " + mb(u)
                        + " of " + mb(limit) + " are held. Ask again later, or raise the engine's memory limit.");
            }
            if (used.compareAndSet(u, u + bytes)) {
                peak.accumulateAndGet(u + bytes, Math::max);
                return;
            }
        }
    }

    private static String mb(long b) {
        return b < 1 << 20 ? b + " bytes" : String.format("%.1f MB", b / 1048576.0);
    }

    /** Bytes reserved now. */
    public long used() {
        return used.get();
    }

    /** The most bytes ever reserved at once. */
    public long peak() {
        return peak.get();
    }

    /** How many reservations were refused. */
    public long refusals() {
        return refusals.get();
    }

    public long limit() {
        return limit;
    }

    /** Sets a new limit (a kit lowers it to prove a refusal is words, then raises it to prove the engine is still usable). */
    public void limit(long bytes) {
        this.limit = bytes;
    }

    private final class Held implements Reservation {
        private long bytes;
        private final String what;
        private boolean closed;

        Held(long bytes, String what) {
            this.bytes = bytes;
            this.what = what;
        }

        @Override
        public synchronized void resize(long next) throws EngineException {
            if (closed) {
                throw new IllegalStateException("the reservation for " + what + " is closed");
            }
            if (next > bytes) {
                take(next - bytes, what);
            } else {
                used.addAndGet(next - bytes);
            }
            bytes = next;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                used.addAndGet(-bytes);
            }
        }
    }
}
