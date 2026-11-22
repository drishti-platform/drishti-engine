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
package com.ash.sample.engines;

import com.ash.engine.Control;
import com.ash.engine.Descriptor;
import com.ash.engine.Descriptors;
import com.ash.engine.Engine;
import com.ash.engine.EngineContext;
import com.ash.engine.EngineMeter;
import com.ash.engine.EngineStats;
import com.ash.engine.Health;
import com.ash.engine.LifecycleState;
import com.ash.engine.Inputs;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;

/**
 * Totals by group over an input read as the caller ({@code mode: request}): the query side of the model on a small figure. When the
 * host offers a grouped scan the source groups and sums; otherwise the engine scans and sums in ID order. Option
 * {@code force-fixed-order} never uses the grouped scan, so AMOUNT is the same bits wherever it runs (specification 8.1 rule 6).
 */
public final class GroupTotalsEngine implements Engine {

    static final String ID = "group-totals";

    private volatile EngineContext context;
    private volatile EngineMeter meter = new EngineMeter(java.time.Clock.systemUTC());
    private volatile LifecycleState lifecycle = LifecycleState.NEW;

    @Override
    public Descriptor descriptor() {
        return Descriptors.of(GroupTotalsEngine.class, ID);
    }

    @Override
    public void init(EngineContext context) {
        this.context = context;
        this.meter = new EngineMeter(context.clock());
        this.lifecycle = LifecycleState.INITIALISED;
    }

    @Override
    public Session session(Snapshot snapshot, Inputs inputs, Control control) {
        return new GroupTotalsSession(context, meter, descriptor().schema("totals"), snapshot, inputs, context.options().flag("force-fixed-order"));
    }

    @Override
    public void start() {
        lifecycle = LifecycleState.RUNNING;
    }

    @Override
    public void suspend() {
        lifecycle = LifecycleState.SUSPENDED;
    }

    @Override
    public void resume() {
        lifecycle = LifecycleState.RUNNING;
    }

    @Override
    public Health health() {
        return Health.of(lifecycle, meter.snapshot().takenAt());
    }

    @Override
    public EngineStats stats() {
        return meter.snapshot();
    }

    @Override
    public void shutdown() {
        lifecycle = LifecycleState.SHUTDOWN;
        context = null;
    }
}
