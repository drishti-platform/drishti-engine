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
import com.ash.engine.Changes;
import com.ash.engine.Descriptor;
import com.ash.engine.Engine;
import com.ash.engine.EngineException;
import com.ash.engine.Filter;
import com.ash.engine.Incremental;
import com.ash.engine.Request;
import com.ash.engine.RequestClass;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import com.ash.engine.Visibility;
import com.ash.engine.host.Budget;
import com.ash.engine.host.EngineJar;
import com.ash.engine.host.HostContext;
import com.ash.engine.host.HostControl;
import com.ash.engine.host.Params;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The kit's host: opens an engine instance with a fresh work folder, the business clock, a bounded executor and a budget; opens a
 * session over a case's rows as a {@link Variation} asks; normalises parameters and builds the visibility as a host does; asks. Every
 * call into the engine runs with the engine's loader as the thread's context loader.
 */
public final class KitHost {

    /** Where engine instances come from: a JAR (as a host meets it) or a class on the class path (the harness). */
    public interface Subject {

        Engine create() throws ReflectiveOperationException;

        Descriptor descriptor();

        ClassLoader loader();

        /** The engine's name in reports: {@code wmean 1.0.0 (engine-sample-0.1.0.jar)}. */
        String label();
    }

    /** A subject made from a registered engine of an opened JAR. */
    public static Subject of(EngineJar jar, EngineJar.Registered r) {
        return new Subject() {
            @Override public Engine create() throws ReflectiveOperationException { return jar.create(r); }
            @Override public Descriptor descriptor() { return r.descriptor(); }
            @Override public ClassLoader loader() { return jar.loader(); }
            @Override public String label() { return r.id() + " " + r.version() + " (" + jar.path().getFileName() + ")"; }
        };
    }

    /** A subject made from an engine class on the class path. */
    public static Subject of(Class<? extends Engine> type) {
        Descriptor d;
        try (Engine e = type.getConstructor().newInstance()) {
            d = e.descriptor();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(type.getName() + " needs a public constructor without parameters", e);
        }
        return new Subject() {
            @Override public Engine create() throws ReflectiveOperationException { return type.getConstructor().newInstance(); }
            @Override public Descriptor descriptor() { return d; }
            @Override public ClassLoader loader() { return type.getClassLoader(); }
            @Override public String label() { return d.id() + " " + d.version() + " (" + type.getSimpleName() + ")"; }
        };
    }

    /**
     * The host's choices for one run. {@code rows}: replaces the case's rows for the inputs it names (a physically changed input);
     * {@code visibility}: replaces the case's ({@code all} or a filter spec); {@code perViewer}: read every input as the viewer.
     */
    public record Variation(int threads, int batchRows, RowInputs.Order order, long shuffleSeed, boolean perViewer, boolean pushdown,
            boolean keepInterim, Long seed, Map<String, List<Map<String, Object>>> rows, Object visibility, long budget,
            Map<String, Object> options, Map<String, Object> params) {

        public static final Variation PLAIN = new Variation(2, 0, RowInputs.Order.NATURAL, 0, false, true, true, null, Map.of(), null,
                1L << 30, Map.of(), null);

        public Variation threads(int n) { return new Variation(n, batchRows, order, shuffleSeed, perViewer, pushdown, keepInterim, seed, rows, visibility, budget, options, params); }
        public Variation batch(int n) { return new Variation(threads, n, order, shuffleSeed, perViewer, pushdown, keepInterim, seed, rows, visibility, budget, options, params); }
        public Variation order(RowInputs.Order o, long s) { return new Variation(threads, batchRows, o, s, perViewer, pushdown, keepInterim, seed, rows, visibility, budget, options, params); }
        public Variation perViewer(boolean b) { return new Variation(threads, batchRows, order, shuffleSeed, b, pushdown, keepInterim, seed, rows, visibility, budget, options, params); }
        public Variation pushdown(boolean b) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, b, keepInterim, seed, rows, visibility, budget, options, params); }
        public Variation keepInterim(boolean b) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, pushdown, b, seed, rows, visibility, budget, options, params); }
        public Variation seed(long s) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, pushdown, keepInterim, s, rows, visibility, budget, options, params); }
        public Variation rows(Map<String, List<Map<String, Object>>> r) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, pushdown, keepInterim, seed, r, visibility, budget, options, params); }
        public Variation visibility(Object v) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, pushdown, keepInterim, seed, rows, v, budget, options, params); }
        public Variation budget(long b) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, pushdown, keepInterim, seed, rows, visibility, b, options, params); }
        public Variation options(Map<String, Object> o) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, pushdown, keepInterim, seed, rows, visibility, budget, o, params); }
        public Variation params(Map<String, Object> p) { return new Variation(threads, batchRows, order, shuffleSeed, perViewer, pushdown, keepInterim, seed, rows, visibility, budget, options, p); }
    }

    private final Subject subject;
    private final Path workRoot;
    private final ElOracle el;
    private final RowInputs.Recorder recorder = new RowInputs.Recorder();
    private final AtomicInteger folders = new AtomicInteger();

    public KitHost(Subject subject, Path workRoot, ElOracle el) {
        this.subject = subject;
        this.workRoot = workRoot;
        this.el = el;
    }

    public Subject subject() {
        return subject;
    }

    public Descriptor descriptor() {
        return subject.descriptor();
    }

    /** Every column read and every undeclared column asked, over every run of this host. */
    public RowInputs.Recorder recorder() {
        return recorder;
    }

    /** Runs {@code call} with the engine's loader as the thread's context loader. */
    public <T> T within(Callable<T> call) throws Exception {
        Thread t = Thread.currentThread();
        ClassLoader before = t.getContextClassLoader();
        t.setContextClassLoader(subject.loader());
        try {
            return call.call();
        } finally {
            t.setContextClassLoader(before);
        }
    }

    /** One full run: open, apply the case's changes, ask, close. Never throws: a failure is an outcome. */
    public Outcome run(Cases.Case c, Variation v) {
        try (Opened o = open(c, v)) {
            if (!c.changes().isEmpty()) {
                Outcome applied = o.apply(c.changes());
                if (applied != null) {
                    return applied;
                }
            }
            return o.ask(o.caseVisibility(), v.seed() != null ? v.seed() : c.request().seed(), o.control());
        } catch (EngineException e) {
            return Outcome.refused(e.code(), e.kind().name(), e.getMessage());
        } catch (Exception | LinkageError | AssertionError e) {
            return Outcome.failed(e);
        }
    }

    /** An engine instance, opened, with a session over the case's rows under {@code v}. */
    public Opened open(Cases.Case c, Variation v) throws Exception {
        Descriptor d = subject.descriptor();
        Map<String, Object> opts = new LinkedHashMap<>(c.given().options());
        opts.putAll(v.options());
        SortedMap<String, Object> options = Params.options(d, opts);
        Path work = Files.createDirectories(workRoot.resolve("instance-" + folders.incrementAndGet()));
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, v.threads()), r -> {
            Thread t = new Thread(r, "kit-engine-" + d.id());
            t.setDaemon(true);
            return t;
        });
        Budget budget = new Budget(v.budget());
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        c.given().rows().forEach(rows::put);
        rows.putAll(v.rows());
        Snapshot snapshot = Snapshot.of(c.given().snapshot());
        Map<String, Filter> viewer = viewer(d, v.visibility() != null ? v.visibility() : c.request().visibility(), v.perViewer());
        RowInputs inputs = new RowInputs(d, rows, c.given().openTables(),
                new RowInputs.Delivery(v.batchRows(), v.order(), v.shuffleSeed(), viewer, v.pushdown()), recorder, el);
        Map<String, RowInputs> bySnapshot = new HashMap<>();
        bySnapshot.put(snapshot.text(), inputs);
        Instant now = c.given().snapshot().values().stream().filter(s -> s.matches("\\d{4}-\\d{2}-\\d{2}")).findFirst()
                .map(s -> Instant.parse(s + "T12:00:00Z")).orElse(Instant.parse("2026-01-01T12:00:00Z"));
        HostContext ctx = new HostContext(d, options, work, Clock.fixed(now, ZoneOffset.UTC), ex, budget, Map.of(), s -> Optional.empty(),
                s -> bySnapshot.containsKey(s.text()) ? bySnapshot.get(s.text()).stamps() : new TreeMap<>());
        Engine engine = within(subject::create);
        Opened o = new Opened(this, c, v, engine, ctx, ex, work, rows, snapshot, bySnapshot);
        try {
            within(() -> {
                engine.open(ctx);
                return null;
            });
            o.session = within(() -> engine.session(snapshot, inputs, o.control()));
        } catch (Exception | Error e) {
            o.close();
            throw e;
        }
        return o;
    }

    /**
     * The request of a case as a host builds it: parameters normalised ({@code v.params()} replace the case's when given), the visibility
     * from the spec ({@code v.visibility()}, else the case's), every filter {@link Filter#ALL} when inputs are read as the viewer.
     */
    public static Request request(Descriptor d, Cases.Case c, Variation v) throws EngineException {
        Map<String, Object> params = v.params() != null ? v.params() : c.request().params();
        SortedMap<String, Object> p = Params.request(d, c.request().outputs(), params);
        Object spec = v.visibility() != null ? v.visibility() : c.request().visibility();
        Map<String, Filter> f = filters(d, spec);
        f.replaceAll((k, x) -> v.perViewer() || asViewer(d, k) ? Filter.ALL : x);
        return new Request(c.id(), c.request().outputs(), p, new Visibility(Cases.signature(spec), f, java.util.Set.of()), "kit",
                v.seed() != null ? v.seed() : c.request().seed(), RequestClass.INTERACTIVE);
    }

    /** True when the host reads {@code input} as the caller whatever the isolation: an input in mode {@code request}, or an open one. */
    static boolean asViewer(Descriptor d, String input) {
        Descriptor.Input in = d.inputs().get(input);
        return in != null && ("request".equals(in.mode()) || in.open());
    }

    /** The rows a host holds back from delivery: every filter when read per viewer, else those of the inputs read as the caller. */
    static Map<String, Filter> viewer(Descriptor d, Object spec, boolean perViewer) {
        Map<String, Filter> f = filters(d, spec);
        f.keySet().removeIf(k -> !perViewer && !asViewer(d, k));
        return f;
    }

    /** The filters of a visibility spec, per input with scope columns ({@code all}: every one {@link Filter#ALL}). */
    static Map<String, Filter> filters(Descriptor d, Object spec) {
        Map<String, Filter> out = new TreeMap<>();
        if ("all".equals(spec)) {
            d.inputs().forEach((n, in) -> {
                if (!in.scopeColumns().isEmpty()) {
                    out.put(n, Filter.ALL);
                }
            });
        } else {
            Cases.map("visibility", spec).forEach((n, f) -> out.put(n, Cases.filter(f)));
        }
        return out;
    }

    /** One opened instance and its session. */
    public static final class Opened implements AutoCloseable {
        private final KitHost host;
        private final Cases.Case c;
        private final Variation v;
        private final Engine engine;
        private final HostContext ctx;
        private final ExecutorService ex;
        private final Path work;
        private final Map<String, List<Map<String, Object>>> rows;
        private final Snapshot snapshot;
        private final Map<String, RowInputs> bySnapshot;
        Session session;
        private boolean closed;

        Opened(KitHost host, Cases.Case c, Variation v, Engine engine, HostContext ctx, ExecutorService ex, Path work,
                Map<String, List<Map<String, Object>>> rows, Snapshot snapshot, Map<String, RowInputs> bySnapshot) {
            this.host = host;
            this.c = c;
            this.v = v;
            this.engine = engine;
            this.ctx = ctx;
            this.ex = ex;
            this.work = work;
            this.rows = rows;
            this.snapshot = snapshot;
            this.bySnapshot = bySnapshot;
        }

        public Engine engine() { return engine; }
        public Session session() { return session; }
        public HostContext context() { return ctx; }
        public Path workDir() { return work; }

        /** A fresh control as the variation asks (interim answers kept or ignored). */
        public HostControl control() {
            return new HostControl(Instant.MAX, Clock.systemUTC(), v.keepInterim());
        }

        /** The visibility spec this run uses. */
        public Object caseVisibility() {
            return v.visibility() != null ? v.visibility() : c.request().visibility();
        }

        /** The request of the case under a visibility spec and a seed, normalised as a host does. */
        public Request request(Object visibilitySpec, long seed) throws EngineException {
            return KitHost.request(host.descriptor(), c, v.visibility(visibilitySpec).seed(seed));
        }

        /** Asks the session; a refusal or failure is an outcome. */
        public Outcome ask(Object visibilitySpec, long seed, HostControl control) {
            try {
                Request r = request(visibilitySpec, seed);
                Answer a = host.within(() -> session.ask(r, control));
                if (a == null) {
                    return Outcome.failed(new IllegalStateException("ask returned null"));
                }
                return Outcome.of(a);
            } catch (EngineException e) {
                return Outcome.refused(e.code(), e.kind().name(), e.getMessage());
            } catch (Exception | LinkageError | AssertionError e) {
                return Outcome.failed(e);
            }
        }

        /** Asks the case's own request. */
        public Outcome ask() {
            return ask(caseVisibility(), v.seed() != null ? v.seed() : c.request().seed(), control());
        }

        /**
         * Applies a case's {@code changes} through {@link Incremental}; when the engine declines, reopens the session over the rows
         * changed physically, as a host does. Null when applied; an outcome when the engine refused or failed.
         */
        public Outcome apply(List<Map<String, Object>> changes) {
            Descriptor d = host.descriptor();
            Map<String, List<Map<String, Object>>> changed = changedRows(d, rows, changes);
            try {
                if (!(session instanceof Incremental inc)) {
                    return Outcome.failed(new IllegalStateException("the case gives changes, but the session does not implement Incremental"));
                }
                List<Changes> list = new ArrayList<>();
                int n = 0;
                for (Map<String, Object> ch : changes) {
                    String input = String.valueOf(ch.get("input"));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> ups = ch.get("upserts") == null ? List.of() : (List<Map<String, Object>>) ch.get("upserts");
                    @SuppressWarnings("unchecked")
                    List<Object> del = ch.get("deleted") == null ? List.of() : (List<Object>) ch.get("deleted");
                    RowInputs.RowBatch b = new RowInputs.RowBatch(d.inputs().get(input).columns(), Math.max(1, ups.size()));
                    b.fill(ups);
                    list.add(new Changes(input, b, del.stream().map(RowInputs::text).toList(), "changed-" + (++n)));
                }
                boolean ok = host.within(() -> inc.apply(list, control()));
                RowInputs now = new RowInputs(d, changed, c.given().openTables(), new RowInputs.Delivery(v.batchRows(), v.order(),
                        v.shuffleSeed(), viewer(d, caseVisibility(), v.perViewer()), v.pushdown()), host.recorder(), host.el);
                bySnapshot.put(snapshot.text(), now);
                if (!ok) {
                    host.within(() -> {
                        session.close();
                        return null;
                    });
                    session = host.within(() -> engine.session(snapshot, now, control()));
                }
                return null;
            } catch (EngineException e) {
                return Outcome.refused(e.code(), e.kind().name(), e.getMessage());
            } catch (Exception | LinkageError | AssertionError e) {
                return Outcome.failed(e);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                host.within(() -> {
                    if (session != null) {
                        session.close();
                    }
                    engine.close();
                    return null;
                });
            } catch (Exception e) {
                // a close that throws is the lifecycle group's finding; the run ends here either way
            } finally {
                ex.shutdownNow();
                deleteTree(work);
            }
        }
    }

    /** The rows of each input after a case's changes: upserts replace rows of the same key or are added; deleted keys are removed. */
    @SuppressWarnings("unchecked")
    static Map<String, List<Map<String, Object>>> changedRows(Descriptor d, Map<String, List<Map<String, Object>>> rows, List<Map<String, Object>> changes) {
        Map<String, List<Map<String, Object>>> out = new TreeMap<>(rows);
        for (Map<String, Object> ch : changes) {
            String input = String.valueOf(ch.get("input"));
            List<String> key = d.inputs().get(input).key();
            List<Map<String, Object>> rs = new ArrayList<>(out.getOrDefault(input, List.of()) == null ? List.of() : out.get(input));
            List<Object> del = ch.get("deleted") == null ? List.of() : (List<Object>) ch.get("deleted");
            for (Object k : del) {
                rs.removeIf(r -> keyOf(key, r).equals(RowInputs.text(k)));
            }
            for (Map<String, Object> up : ch.get("upserts") == null ? List.<Map<String, Object>>of() : (List<Map<String, Object>>) ch.get("upserts")) {
                int at = -1;
                for (int i = 0; i < rs.size(); i++) {
                    if (!key.isEmpty() && keyOf(key, rs.get(i)).equals(keyOf(key, up))) {
                        at = i;
                    }
                }
                if (at >= 0) {
                    rs.set(at, up);
                } else {
                    rs.add(up);
                }
            }
            out.put(input, rs);
        }
        return out;
    }

    static String keyOf(List<String> key, Map<String, Object> row) {
        return String.join("|", key.stream().map(k -> RowInputs.text(row.get(k))).toList());
    }

    static void deleteTree(Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(x);
            }
        } catch (IOException e) {
            // a folder left behind is listed by the run's own clean-up of its work root
        }
    }
}
