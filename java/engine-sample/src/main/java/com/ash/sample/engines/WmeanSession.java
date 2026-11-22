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
import com.ash.engine.Answers;
import com.ash.engine.Cell;
import com.ash.engine.Changes;
import com.ash.engine.Control;
import com.ash.engine.EngineContext;
import com.ash.engine.EngineException;
import com.ash.engine.EngineMeter;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.Explaining;
import com.ash.engine.Explanation;
import com.ash.engine.Filter;
import com.ash.engine.Incremental;
import com.ash.engine.MemoryBudget;
import com.ash.engine.Missing;
import com.ash.engine.Partial;
import com.ash.engine.Partitioned;
import com.ash.engine.Request;
import com.ash.engine.Session;
import com.ash.engine.Shard;
import com.ash.engine.Snapshot;
import com.ash.engine.Stamps;
import com.ash.engine.Step;
import com.ash.engine.Steps;
import com.ash.engine.Table;
import com.ash.engine.TableSchema;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A wmean session: the observations of one snapshot, replaced whole by {@link #apply}. Holds nothing of a viewer. */
final class WmeanSession implements Session, Explaining, Incremental, Partitioned {

    private final EngineMeter meter;

    private final EngineContext context;
    private final Snapshot snapshot;
    private final List<String> lacking;
    private final MemoryBudget.Reservation hold;
    private final TableSchema schema;
    private volatile Observations rows;
    private volatile boolean closed;

    WmeanSession(EngineContext context, EngineMeter meter, TableSchema schema, Snapshot snapshot, Observations rows, List<String> lacking,
            MemoryBudget.Reservation hold) {
        this.meter = meter;
        meter.sessionOpened();
        this.context = context;
        this.schema = schema;
        this.snapshot = snapshot;
        this.rows = rows;
        this.lacking = List.copyOf(lacking);
        this.hold = hold;
    }

    @Override
    public Snapshot snapshot() {
        return snapshot;
    }

    @Override
    public long memoryBytes() {
        Observations r = rows;
        return closed || r == null ? 0 : 65_536 + r.bytes();
    }

    @Override
    public Answer ask(Request request, Control control) throws EngineException {
        return meter.measure(() -> compute(request, control), 0, 0);
    }

    private Answer compute(Request request, Control control) throws EngineException {
        Observations base = rows;
        if (base == null) {
            return Answers.notFound(context, this, request,
                    List.of(new Missing(WmeanEngine.INPUT, "stats/observation@1", snapshot, lacking, request.outputs())));
        }
        Filter see = visibility(request);
        double min = ((Number) request.param("min-weight")).doubleValue();
        Observations used = base.overlaid((Table) request.param("what-if"));
        if (used.size() > 1) {
            Sums first = Sums.of(used.head(used.size() / 2), see, min, control);
            control.interim(Answers.interim(context, this, request, List.of(first.table(schema)),
                    "estimated from the first half of the observations in ID order"));
        }
        Sums sums = Sums.of(used, see, min, control);
        return Answers.complete(context, this, request, List.of(sums.table(schema)));
    }

    private static Filter visibility(Request request) throws EngineException {
        Filter see = request.visibility() == null ? null : request.visibility().rows().get(WmeanEngine.INPUT);
        if (see == null) {
            throw new EngineException("ENG-4002", Kind.NOT_PERMITTED, "the request names no rows of observations it may read");
        }
        return see;
    }

    @Override
    public Explanation explain(Request request, Cell cell, Control control) throws EngineException {
        Observations base = rows;
        String group = cell.key().get("GROUP");
        if (base == null || !WmeanEngine.OUTPUT.equals(cell.output()) || !"MEAN".equals(cell.column()) || group == null) {
            throw new EngineException("ENG-3005", Kind.REFUSED, "only MEAN of mean-by-group, for a group in the answer, is explained");
        }
        Filter see = visibility(request);
        double min = ((Number) request.param("min-weight")).doubleValue();
        Observations used = base.overlaid((Table) request.param("what-if"));
        if (!see.test(c -> "GROUP".equals(c) ? group : null)) {
            throw new EngineException("ENG-3005", Kind.REFUSED, "the group " + group + " is not in this request's answer");
        }
        List<Map<String, Object>> list = new ArrayList<>();
        double weight = 0;
        double product = 0;
        for (int i : used.rows(group::equals)) {
            if (used.weight[i] < min) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", used.id[i]);
            m.put("VALUE", used.value[i]);
            m.put("WEIGHT", used.weight[i]);
            m.put("PRODUCT", used.value[i] * used.weight[i]);
            list.add(m);
            weight += used.weight[i];
            product += used.value[i] * used.weight[i];
        }
        if (list.isEmpty() || weight <= 0) {
            throw new EngineException("ENG-3005", Kind.REFUSED, "the group " + group + " is not in this request's answer");
        }
        double mean = product / weight;
        List<Step> steps = List.of(
                Steps.of("product", "Sum of value × weight over the group's " + list.size() + " observations", "sum($.rows, 'PRODUCT')", Map.of("rows", list), product),
                Steps.of("weight", "Sum of weight", "sum($.rows, 'WEIGHT')", Map.of("rows", list), weight),
                Steps.of("mean", "Weighted mean", "step.product / step.weight", Map.of(), mean));
        return new Explanation(cell, mean, steps);
    }

    @Override
    public void workbook(Request request, OutputStream out, Control control) throws EngineException, IOException {
        Answer a = ask(request, new QuietControl(control));
        Observations base = rows;
        Filter see = visibility(request);
        Observations used = base == null ? null : base.overlaid((Table) request.param("what-if"));
        WmeanBook.write(out, used, see, ((Number) request.param("min-weight")).doubleValue(), a.tables().isEmpty() ? null : a.tables().get(0));
    }

    @Override
    public boolean apply(List<Changes> changes, Control control) throws EngineException {
        Observations now = rows;
        if (now == null || changes.stream().anyMatch(c -> !WmeanEngine.INPUT.equals(c.input()))) {
            return false;
        }
        control.check();
        Observations next = now.with(changes);
        if (hold != null) {
            hold.resize(65_536 + next.bytes());
        }
        rows = next;
        return true;
    }

    @Override
    public Partial partial(Request request, Shard shard, Control control) throws EngineException {
        Observations base = rows;
        if (base == null) {
            throw new EngineException("ENG-5021", Kind.REFUSED, "observations are not there for this snapshot; there is nothing to split");
        }
        Filter see = visibility(request);
        Filter both = new Filter.And(List.of(see, shard.rows()));
        double min = ((Number) request.param("min-weight")).doubleValue();
        Sums sums = Sums.of(base.overlaid((Table) request.param("what-if")), both, min, control);
        return new Partial(shard.id(), List.of(sums.partial()), Stamps.of(context, this, request));
    }

    @Override
    public void close() {
        meter.sessionClosed();
        closed = true;
        rows = null;
        if (hold != null) {
            hold.close();
        }
    }
}
