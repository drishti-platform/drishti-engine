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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coalescing of trigger firings (specification 9.3 rule 1): a firing waits {@code quiet} for the next; a burst fires at most
 * {@code max-wait} after its first firing. The host's scheduler runs it; never two runs at once.
 */
public final class Coalescer {

    private final ScheduledExecutorService scheduler;
    private final Duration quiet;
    private final Duration maxWait;
    private final Runnable fire;
    private final Clock clock;
    private ScheduledFuture<?> pending;
    private Instant first;
    private boolean running;
    private boolean again;

    public Coalescer(ScheduledExecutorService scheduler, Duration quiet, Duration maxWait, Clock clock, Runnable fire) {
        this.scheduler = scheduler;
        this.quiet = quiet;
        this.maxWait = maxWait;
        this.clock = clock;
        this.fire = fire;
    }

    /** One firing. */
    public synchronized void signal() {
        Instant now = clock.instant();
        if (first == null) {
            first = now;
        }
        if (pending != null) {
            pending.cancel(false);
        }
        long waitMs = Math.max(0, Math.min(quiet.toMillis(), Duration.between(now, first.plus(maxWait)).toMillis()));
        pending = scheduler.schedule(this::run, waitMs, TimeUnit.MILLISECONDS);
    }

    private void run() {
        synchronized (this) {
            if (running) {
                again = true;
                return;
            }
            running = true;
            pending = null;
            first = null;
        }
        try {
            fire.run();
        } finally {
            boolean more;
            synchronized (this) {
                running = false;
                more = again;
                again = false;
            }
            if (more) {
                signal();
            }
        }
    }

    /** Parses {@code 200ms}, {@code 5s}, {@code 2m}. */
    public static Duration duration(String text, Duration dflt) {
        if (text == null || text.isBlank()) {
            return dflt;
        }
        var m = java.util.regex.Pattern.compile("(\\d+)(ms|s|m)").matcher(text.strip());
        if (!m.matches()) {
            throw new IllegalArgumentException("a duration such as 200ms, 5s or 2m: " + text);
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "ms" -> Duration.ofMillis(n);
            case "s" -> Duration.ofSeconds(n);
            default -> Duration.ofMinutes(n);
        };
    }
}
