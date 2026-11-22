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

import com.ash.engine.Answer;
import com.ash.engine.Assembling;
import com.ash.engine.Control;
import com.ash.engine.Descriptor;
import com.ash.engine.Descriptors;
import com.ash.engine.Engine;
import com.ash.engine.EngineContext;
import com.ash.engine.EngineMeter;
import com.ash.engine.EngineStats;
import com.ash.engine.Health;
import com.ash.engine.LifecycleState;
import com.ash.engine.EngineException;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.InputTable;
import com.ash.engine.Inputs;
import com.ash.engine.MemoryBudget;
import com.ash.engine.Partial;
import com.ash.engine.Request;
import com.ash.engine.Scan;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import com.ash.engine.Stamp;
import com.ash.engine.Status;
import com.ash.engine.Table;
import com.ash.engine.TableSchema;
import com.ash.engine.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The weighted mean of a value by group (the specification's worked example, section 16). It shows the whole contract on a small
 * figure: a session over a snapshot, a fixed order of summation, the missing table as an answer, the visibility refused when absent,
 * changes applied atomically, partials and their assembly, a what-if table over the input, an interim answer, explanations whose
 * formulas recompute, and a formula workbook.
 */
public final class WmeanEngine implements Engine, Assembling {

    static final String ID = "wmean";
    static final String INPUT = "observations";
    static final String OUTPUT = "mean-by-group";
    static final List<String> COLUMNS = List.of("ID", "GROUP", "VALUE", "WEIGHT");

    private volatile EngineContext context;
    private volatile EngineMeter meter = new EngineMeter(java.time.Clock.systemUTC());
    private volatile LifecycleState lifecycle = LifecycleState.NEW;
    private volatile TableSchema schema;

    @Override
    public Descriptor descriptor() {
        return Descriptors.of(WmeanEngine.class, ID);
    }

    @Override
    public void init(EngineContext context) {
        this.context = context;
        this.meter = new EngineMeter(context.clock());
        this.lifecycle = LifecycleState.INITIALISED;
        this.schema = descriptor().schema(OUTPUT);
    }

    @Override
    public Session session(Snapshot snapshot, Inputs inputs, Control control) throws EngineException {
        InputTable t = inputs.table(INPUT);
        if (!t.present()) {
            return new WmeanSession(context, meter, schema, snapshot, null, List.of(), null);
        }
        List<String> lacking = COLUMNS.stream().filter(c -> !t.columns().contains(c)).toList();
        if (!lacking.isEmpty()) {
            return new WmeanSession(context, meter, schema, snapshot, null, lacking, null);
        }
        MemoryBudget.Reservation hold = context.memory().reserve(65_536 + 40L * Math.max(t.rows(), 0), INPUT);
        try {
            Observations.Builder rows = new Observations.Builder();
            t.scan(new Scan(COLUMNS, Filter.ALL, 4_096), batch -> {
                control.check();
                rows.add(batch);
            });
            return new WmeanSession(context, meter, schema, snapshot, rows.build(), List.of(), hold);
        } catch (EngineException | RuntimeException e) {
            hold.close();
            throw e;
        }
    }

    @Override
    public Answer assemble(Request request, List<Partial> partials, Control control) throws EngineException {
        if (partials.isEmpty()) {
            throw new EngineException("ENG-5021", Kind.REFUSED, "no partials to assemble");
        }
        Stamp first = partials.get(0).stamp();
        List<Table> tables = new ArrayList<>();
        for (Partial p : partials) {
            Stamp s = p.stamp();
            if (!Objects.equals(s.version(), first.version()) || !Objects.equals(s.options(), first.options())
                    || !Objects.equals(s.request(), first.request()) || s.seed() != first.seed()) {
                throw new EngineException("ENG-5021", Kind.REFUSED, "partials of another version, options, request or seed cannot be assembled together");
            }
            tables.addAll(p.aggregates());
        }
        control.check();
        Table out = Sums.merge(tables).table(schema);
        return new Answer(Status.COMPLETE, List.of(out), List.of(), first, List.of());
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
