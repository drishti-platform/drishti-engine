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

import com.ash.engine.Answer;
import com.ash.engine.Control;
import com.ash.engine.EngineException;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** A request's control as a host gives it: cancel, a deadline on the host's clock, progress, the interim answers kept (or ignored). */
public final class HostControl implements Control {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicLong checks = new AtomicLong();
    private final Instant deadline;
    private final Clock clock;
    private final boolean keepInterim;
    private final List<Answer> interims = new CopyOnWriteArrayList<>();
    private volatile double progress;

    /** A control with {@code deadline} on {@code clock} (the wall clock of the host, never the engine's); {@code keepInterim}: false ignores them. */
    public HostControl(Instant deadline, Clock clock, boolean keepInterim) {
        this.deadline = deadline;
        this.clock = clock;
        this.keepInterim = keepInterim;
    }

    /** A control with no deadline that keeps interim answers. */
    public static HostControl open() {
        return new HostControl(Instant.MAX, Clock.systemUTC(), true);
    }

    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public boolean cancelled() {
        return cancelled.get();
    }

    @Override
    public void check() throws EngineException {
        checks.incrementAndGet();
        if (cancelled.get()) {
            throw new EngineException("ENG-5010", Kind.CANCELLED, "cancelled");
        }
        if (deadline != Instant.MAX && clock.instant().isAfter(deadline)) {
            throw new EngineException("ENG-5011", Kind.TIMED_OUT, "the deadline passed");
        }
    }

    @Override
    public Instant deadline() {
        return deadline;
    }

    @Override
    public void progress(double fraction, String words) {
        progress = fraction;
    }

    @Override
    public void interim(Answer answer) {
        if (answer == null || answer.status() != Status.INTERIM) {
            throw new IllegalArgumentException("an interim answer has the status INTERIM");
        }
        if (keepInterim) {
            interims.add(answer);
        }
    }

    /** How many times the engine called {@link #check()}. */
    public long checks() {
        return checks.get();
    }

    /** The interim answers given, in order (empty when they are ignored). */
    public List<Answer> interims() {
        return List.copyOf(interims);
    }

    public double progress() {
        return progress;
    }
}
