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
package com.ash.engine.tck;

import com.ash.engine.inputs.RowInputs;
import com.ash.engine.Answer;
import com.ash.engine.Assembling;
import com.ash.engine.Descriptor;
import com.ash.engine.Filter;
import com.ash.engine.Partial;
import com.ash.engine.Partitioned;
import com.ash.engine.Request;
import com.ash.engine.Shard;
import com.ash.engine.Status;
import com.ash.engine.Table;
import com.ash.engine.TableSchema;
import com.ash.engine.host.HostControl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The laws of section 12.4: each derives runs from a case the author wrote and checks a property that holds for any engine. A law that
 * does not apply to a case (no scope column, no optional input, a capability not listed) gives no line.
 */
public final class Laws {

    /** Every law, in the order a run reports them. */
    public static final List<String> ALL = List.of("repeat", "threads", "batching", "order", "filter-equivalence", "interleaving",
            "incremental", "additivity", "binding", "absence", "seed", "explain", "overlay", "pushdown", "interim", "isolation");

    private final KitHost host;
    private final Descriptor d;

    public Laws(KitHost host) {
        this.host = host;
        this.d = host.descriptor();
    }

    /** Every law that applies to {@code c}, with {@code base} the case's own outcome (the plain run). */
    public List<Result> run(Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        if (base.failed()) {
            return out;                                  // the case itself failed: its line says why
        }
        boolean refusal = !base.answered();
        KitHost.Variation p = KitHost.Variation.PLAIN;
        if (c.laws().runs("repeat")) {
            out.add(repeat(c, base));
        }
        if (c.laws().runs("threads")) {
            out.add(compare(c, "threads", base, List.of(p.threads(1), p.threads(4), p.threads(Math.max(4, Runtime.getRuntime().availableProcessors()))), "executors of 1, 4 and all threads"));
        }
        if (c.laws().runs("batching")) {
            out.add(compare(c, "batching", base, List.of(p.batch(1), p.batch(7), p.batch(4096)), "batches of 1, 7 and 4,096 rows"));
        }
        if (c.laws().runs("order") && d.inputs().values().stream().allMatch(i -> i.orderedBy().isEmpty())) {
            out.add(compare(c, "order", base, List.of(p.order(RowInputs.Order.REVERSED, 0), p.order(RowInputs.Order.SHUFFLED, 42)),
                    "rows reversed and shuffled"));
        }
        Object derived = derivedVisibility(c);
        if (!refusal && c.laws().runs("filter-equivalence") && derived != null) {
            out.add(filterEquivalence(c, base, derived));
        }
        if (!refusal && c.laws().runs("interleaving") && derived != null) {
            out.add(interleaving(c, derived));
        }
        if (d.has("incremental") && c.laws().runs("incremental") && !refusal) {
            out.addAll(incremental(c, base));
        }
        if (d.has("partition") && c.laws().runs("additivity") && !refusal && c.changes().isEmpty() && base.answer().status() == Status.COMPLETE) {
            out.add(additivity(c, base));
        }
        if (d.has("process") && c.laws().runs("binding")) {
            out.add(Result.fail("laws", c.name(), "binding", "the capability process is listed; this kit has no process binding to prove it"));
        }
        if (c.laws().runs("absence") && !refusal) {
            out.addAll(absence(c, base));
        }
        if (c.laws().runs("seed")) {
            out.add(seed(c, base));
        }
        if (c.laws().runs("overlay") && !refusal) {
            out.addAll(overlay(c, base));
        }
        if (c.laws().runs("pushdown")) {
            out.addAll(pushdown(c, base));
        }
        if (d.has("progressive") && c.laws().runs("interim")) {
            out.add(interim(c, base));
        }
        if (c.laws().runs("isolation") && !refusal && d.inputs().values().stream().anyMatch(i -> !i.scopeColumns().isEmpty())) {
            out.add(compare(c, "isolation", base, List.of(p.perViewer(true)), "every input read as the viewer, a session per signature"));
        }
        if (c.laws().why() != null) {
            out.add(Result.ok("laws", c.name(), "skipped", c.laws().skip() + ": " + c.laws().why()));
        }
        return out;
    }

    private Result compare(Cases.Case c, String law, Outcome base, List<KitHost.Variation> vs, String words) {
        for (KitHost.Variation v : vs) {
            String diff = Compare.same(base, host.run(c, v));
            if (diff != null) {
                return Result.fail("laws", c.name(), law, words + ": " + diff);
            }
        }
        return Result.of("laws", c.name(), law, null, base.hash());
    }

    private Result repeat(Cases.Case c, Outcome base) {
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            if (!c.changes().isEmpty() && o.apply(c.changes()) != null) {
                return Result.fail("laws", c.name(), "repeat", "the changes did not apply on a second open");
            }
            for (int i = 0; i < 3; i++) {
                String diff = Compare.same(base, o.ask());
                if (diff != null) {
                    return Result.fail("laws", c.name(), "repeat", "ask " + (i + 1) + " on one session: " + diff);
                }
            }
        } catch (Exception e) {
            if (base.answered() || !(e instanceof com.ash.engine.EngineException ee && ee.code().equals(base.code()))) {
                return Result.fail("laws", c.name(), "repeat", "a second open: " + e);
            }
        }
        String diff = Compare.same(base, host.run(c, KitHost.Variation.PLAIN));
        return Result.of("laws", c.name(), "repeat", diff == null ? null : "a new instance: " + diff, base.hash());
    }

    /** The visibility the access laws compare with: the case's own when it names one; for an {@code all} case, one the kit derives. */
    Object derivedVisibility(Cases.Case c) {
        Object v = c.request().visibility();
        if (!"all".equals(v)) {
            return v;
        }
        for (Map.Entry<String, Descriptor.Input> e : d.inputs().entrySet()) {
            List<Map<String, Object>> rows = c.given().rows().get(e.getKey());
            if (e.getValue().scopeColumns().isEmpty() || rows == null || rows.isEmpty()) {
                continue;
            }
            String col = e.getValue().scopeColumns().get(0);
            TreeSet<String> values = new TreeSet<>();
            rows.forEach(r -> {
                if (r.get(col) != null) {
                    values.add(RowInputs.text(r.get(col)));
                }
            });
            if (values.isEmpty()) {
                continue;
            }
            Map<String, Object> spec = new LinkedHashMap<>();
            d.inputs().forEach((n, in) -> {
                if (!in.scopeColumns().isEmpty()) {
                    spec.put(n, "all");
                }
            });
            spec.put(e.getKey(), Map.of("eq", List.of(col, values.first())));
            return spec;
        }
        return null;
    }

    /** The case's rows reduced to the visibility's filters (rows outside it physically removed). */
    private Map<String, List<Map<String, Object>>> reduced(Cases.Case c, Object spec) {
        Map<String, Filter> f = KitHost.filters(d, spec);
        Map<String, List<Map<String, Object>>> out = new TreeMap<>();
        f.forEach((input, filter) -> {
            List<Map<String, Object>> rows = c.given().rows().get(input);
            if (rows != null) {
                out.put(input, rows.stream().filter(r -> filter.test(col -> RowInputs.text(r.get(col)))).toList());
            }
        });
        return out;
    }

    private Result filterEquivalence(Cases.Case c, Outcome base, Object v) {
        Outcome underV = "all".equals(c.request().visibility()) ? host.run(c, KitHost.Variation.PLAIN.visibility(v)) : base;
        if (!c.changes().isEmpty()) {
            return Result.ok("laws", c.name(), "filter-equivalence", "not derived for a case with changes");
        }
        Map<String, Filter> f = KitHost.filters(d, v);
        Map<String, Object> params = new LinkedHashMap<>(c.request().params());
        d.params().forEach((name, t) -> {
            Filter of = t.overlays() == null ? null : f.get(t.overlays());
            if (of != null && params.get(name) instanceof List<?> rows) {
                params.put(name, rows.stream().filter(r -> r instanceof Map<?, ?> m && of.test(col -> RowInputs.text(m.get(col)))).toList());
            }
        });
        Outcome physical = host.run(c, KitHost.Variation.PLAIN.rows(reduced(c, v)).visibility("all").params(params));
        String diff = Compare.same(underV, physical);
        return Result.of("laws", c.name(), "filter-equivalence", diff == null ? null : "under the visibility and over rows reduced to it: " + diff, underV.hash());
    }

    private Result interleaving(Cases.Case c, Object other) {
        Object first = c.request().visibility();
        Object second = "all".equals(first) ? other : "all";
        boolean perSignature = !d.inputs().isEmpty() && d.inputs().keySet().stream().allMatch(i -> KitHost.asViewer(d, i));
        try (KitHost.Opened o1 = host.open(c, KitHost.Variation.PLAIN.threads(4).visibility(first));
                KitHost.Opened o2 = perSignature ? host.open(c, KitHost.Variation.PLAIN.threads(4).visibility(second)) : null) {
            KitHost.Opened s2 = perSignature ? o2 : o1;
            if (!c.changes().isEmpty() && (o1.apply(c.changes()) != null || perSignature && o2.apply(c.changes()) != null)) {
                return Result.fail("laws", c.name(), "interleaving", "the changes did not apply");
            }
            Outcome solo1 = o1.ask(first, c.request().seed(), o1.control());
            Outcome solo2 = s2.ask(second, c.request().seed(), s2.control());
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Future<String>> fs = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    boolean one = i % 2 == 0;
                    KitHost.Opened s = one ? o1 : s2;
                    fs.add(pool.submit(() -> Compare.same(one ? solo1 : solo2, s.ask(one ? first : second, c.request().seed(), s.control()))));
                }
                for (Future<String> f : fs) {
                    String diff = f.get();
                    if (diff != null) {
                        return Result.fail("laws", c.name(), "interleaving", "two viewers from 8 threads: " + diff);
                    }
                }
            } finally {
                pool.shutdownNow();
            }
            return Result.of("laws", c.name(), "interleaving", null, solo1.hash());
        } catch (Exception e) {
            return Result.fail("laws", c.name(), "interleaving", e.toString());
        }
    }

    private List<Result> incremental(Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        if (!c.changes().isEmpty()) {
            Map<String, List<Map<String, Object>>> changed = KitHost.changedRows(d, c.given().rows(), c.changes());
            Outcome fresh = host.run(without(c), KitHost.Variation.PLAIN.rows(changed));
            String diff = Compare.same(base, fresh);
            out.add(Result.of("laws", c.name(), "incremental", diff == null ? null : "applied changes and a fresh session over the changed rows: " + diff, base.hash()));
            return out;
        }
        for (Map.Entry<String, Descriptor.Input> e : d.inputs().entrySet()) {
            List<Map<String, Object>> rows = c.given().rows().get(e.getKey());
            if (!"rows".equals(e.getValue().changes()) || rows == null || rows.size() < 2) {
                continue;
            }
            int half = rows.size() / 2;
            Map<String, List<Map<String, Object>>> first = new TreeMap<>(c.given().rows());
            first.put(e.getKey(), rows.subList(0, half));
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("input", e.getKey());
            change.put("upserts", rows.subList(half, rows.size()));
            Outcome applied;
            try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN.rows(first))) {
                Outcome refused = o.apply(List.of(change));
                applied = refused != null ? refused : o.ask();
            } catch (Exception ex) {
                applied = Outcome.failed(ex);
            }
            String diff = Compare.same(base, applied);
            out.add(Result.of("laws", c.name(), "incremental", diff == null ? null : "half of " + e.getKey() + " withheld, then applied: " + diff, base.hash()));
        }
        return out;
    }

    static Cases.Case without(Cases.Case c) {
        return new Cases.Case(c.suite(), c.engine(), c.since(), c.id(), c.source(), c.reference(), c.given(), c.request(), c.expect(), c.explain(),
                List.of(), c.laws(), c.file());
    }

    private Result additivity(Cases.Case c, Outcome base) {
        Descriptor.Partition part = d.partition();
        List<Map<String, Object>> rows = c.given().rows().get(part.input());
        TreeSet<String> values = new TreeSet<>();
        if (rows != null) {
            rows.forEach(r -> values.add(RowInputs.text(r.get(part.unit()))));
        }
        overlayRows(c, part.input()).forEach(r -> values.add(RowInputs.text(r.get(part.unit()))));
        values.remove(null);
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            if (!(o.session() instanceof Partitioned ps) || !(o.engine() instanceof Assembling as)) {
                return Result.fail("laws", c.name(), "additivity", "the capability partition is listed but the session is not Partitioned or the engine not Assembling");
            }
            List<String> vs = new ArrayList<>(values);
            for (int k = 1; k <= 3; k++) {
                List<Partial> partials = new ArrayList<>();
                Request r = o.request(o.caseVisibility(), c.request().seed());
                HostControl ctl = o.control();
                for (int s = 0; s < k; s++) {
                    Set<String> mine = new TreeSet<>();
                    for (int i = s; i < vs.size(); i += k) {
                        mine.add(vs.get(i));
                    }
                    Shard shard = new Shard("shard-" + k + "-" + s, new Filter.In(part.unit(), mine));
                    partials.add(host.within(() -> ps.partial(r, shard, ctl)));
                }
                Answer a = host.within(() -> as.assemble(r, partials, ctl));
                String diff = Compare.same(base, Outcome.of(a));
                if (diff != null) {
                    return Result.fail("laws", c.name(), "additivity", "split " + k + " ways and assembled: " + diff);
                }
            }
            return Result.of("laws", c.name(), "additivity", null, base.hash());
        } catch (com.ash.engine.EngineException e) {
            String diff = Compare.same(base, Outcome.refused(e.code(), e.kind().name(), e.getMessage()));
            return Result.of("laws", c.name(), "additivity", diff, base.hash());
        } catch (Exception e) {
            return Result.fail("laws", c.name(), "additivity", e.toString());
        }
    }

    /** The rows of the case's table parameters that overlay {@code input}. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> overlayRows(Cases.Case c, String input) {
        List<Map<String, Object>> out = new ArrayList<>();
        d.params().forEach((name, t) -> {
            if (input.equals(t.overlays()) && c.request().params().get(name) instanceof List<?> rows) {
                rows.forEach(r -> out.add((Map<String, Object>) r));
            }
        });
        return out;
    }

    private List<Result> absence(Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        for (Map.Entry<String, Descriptor.Input> e : d.inputs().entrySet()) {
            if (e.getValue().required() || e.getValue().open() || c.given().rows().get(e.getKey()) == null) {
                continue;
            }
            Map<String, List<Map<String, Object>>> rows = new TreeMap<>(c.given().rows());
            rows.put(e.getKey(), null);
            Outcome without = host.run(c, KitHost.Variation.PLAIN.rows(rows));
            Set<String> needing = new TreeSet<>();
            for (String o : c.request().outputs()) {
                if (d.needs(o).contains(e.getKey())) {
                    needing.add(o);
                }
            }
            String diff = Compare.same(base, without, needing);
            if (diff == null && !needing.isEmpty() && (!without.answered() || without.answer().missing().stream().noneMatch(m -> m.input().equals(e.getKey())))) {
                diff = "the outputs " + needing + " need " + e.getKey() + " but the answer does not name it missing";
            }
            out.add(Result.of("laws", c.name(), "absence", diff == null ? null : e.getKey() + " removed: " + diff, without.hash()));
        }
        return out;
    }

    private Result seed(Cases.Case c, Outcome base) {
        Set<String> stochastic = new TreeSet<>();
        d.outputs().forEach((n, o) -> {
            if (o.stochastic()) {
                stochastic.add(n);
            }
        });
        Outcome other = host.run(c, KitHost.Variation.PLAIN.seed(c.request().seed() + 1));
        String diff = Compare.same(base, other, stochastic);
        return Result.of("laws", c.name(), "seed", diff == null ? null : "another seed: " + diff, base.hash());
    }

    private List<Result> overlay(Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        for (Map.Entry<String, Descriptor.Typed> e : d.params().entrySet()) {
            if (!"table".equals(e.getValue().type()) || e.getValue().overlays() == null || !c.request().params().containsKey(e.getKey())) {
                continue;
            }
            String input = e.getValue().overlays();
            Object given = c.request().params().get(e.getKey());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> overlay = given instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("input", input);
            change.put("upserts", overlay);
            Map<String, List<Map<String, Object>>> physical = KitHost.changedRows(d, c.given().rows(), List.of(change));
            Map<String, Object> params = new LinkedHashMap<>(c.request().params());
            params.remove(e.getKey());
            Outcome plain = host.run(c, KitHost.Variation.PLAIN.rows(physical).params(params));
            String diff = Compare.same(base, plain);
            out.add(Result.of("laws", c.name(), "overlay", diff == null ? null : e.getKey() + " over " + input + " and the input changed physically: " + diff, base.hash()));
        }
        return out;
    }

    private List<Result> pushdown(Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        Outcome refused = host.run(c, KitHost.Variation.PLAIN.pushdown(false));
        String diff = Compare.same(base, refused);
        boolean relative = d.outputs().values().stream().flatMap(o -> o.columns().stream()).anyMatch(col -> col.exact() != null && col.exact().startsWith("relative:"));
        if (diff != null || !relative) {
            out.add(Result.of("laws", c.name(), "pushdown", diff == null ? null : "grouped scans offered and refused: " + diff, base.hash()));
            return out;
        }
        String declared = d.exactPathOption();
        if (declared == null) {
            out.add(Result.fail("laws", c.name(), "pushdown", "a column is relative, so a bool option must declare forces: exact-path (section 8.1 rule 6)"));
            return out;
        }
        List<String> forcing = List.of(declared);
        for (String f : forcing) {
            Map<String, Object> opts = new LinkedHashMap<>(c.given().options());
            opts.put(f, true);
            Outcome offered = host.run(c, KitHost.Variation.PLAIN.options(opts));
            Outcome notOffered = host.run(c, KitHost.Variation.PLAIN.options(opts).pushdown(false));
            if (!offered.hash().equals(notOffered.hash())) {
                out.add(Result.fail("laws", c.name(), "pushdown", "with " + f + ": true the answer is not the same bits offered and refused"));
                return out;
            }
        }
        out.add(Result.of("laws", c.name(), "pushdown", null, base.hash()));
        return out;
    }

    private Result interim(Cases.Case c, Outcome base) {
        Outcome ignored = host.run(c, KitHost.Variation.PLAIN.keepInterim(false));
        String diff = Compare.same(base, ignored);
        if (diff != null) {
            return Result.fail("laws", c.name(), "interim", "a control that ignores interim answers: " + diff);
        }
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            if (!c.changes().isEmpty() && o.apply(c.changes()) != null) {
                return Result.fail("laws", c.name(), "interim", "the changes did not apply");
            }
            HostControl ctl = o.control();
            o.ask(o.caseVisibility(), c.request().seed(), ctl);
            Map<String, Integer> per = new TreeMap<>();
            for (Answer a : ctl.interims()) {
                if (a.status() != Status.INTERIM || a.notes().isEmpty()) {
                    return Result.fail("laws", c.name(), "interim", "an interim answer has the status INTERIM and a note that says how it was estimated");
                }
                for (Table t : a.tables()) {
                    TableSchema want = d.schema(t.schema().name());
                    if (!want.columns().equals(t.schema().columns())) {
                        return Result.fail("laws", c.name(), "interim", "an interim table of " + t.schema().name() + " does not have the output's schema");
                    }
                    if (per.merge(t.schema().name(), 1, Integer::sum) > 1) {
                        return Result.fail("laws", c.name(), "interim", "more than one interim answer for " + t.schema().name());
                    }
                }
            }
            return Result.of("laws", c.name(), "interim", null, base.hash());
        } catch (Exception e) {
            return Result.fail("laws", c.name(), "interim", e.toString());
        }
    }
}
