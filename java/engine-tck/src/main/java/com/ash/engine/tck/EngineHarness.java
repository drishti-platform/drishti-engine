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

import com.ash.engine.Answer;
import com.ash.engine.Cell;
import com.ash.engine.Engine;
import com.ash.engine.EngineException;
import com.ash.engine.Explaining;
import com.ash.engine.Explanation;
import com.ash.engine.Request;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The JUnit harness of section 6 for an author's own unit tests: an engine class, in-memory rows per input, options, a budget and a
 * temporary work folder give a session to ask. No JAR, no case file, no host; needs a JDK only (JUnit is the author's).
 *
 * <pre>
 * try (var s = EngineHarness.of(WmeanEngine.class).snapshot("date", "2026-01-13")
 *         .rows("observations", List.of(Map.of("ID", "o1", "GROUP", "A", "VALUE", 10.0, "WEIGHT", 1.0))).open()) {
 *     Answer a = s.ask(List.of("mean-by-group"), Map.of());
 * }
 * </pre>
 */
public final class EngineHarness {

    private final Class<? extends Engine> type;
    private final Map<String, String> snapshot = new TreeMap<>();
    private final Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
    private final Map<String, Object> options = new LinkedHashMap<>();
    private long budget = 1L << 30;
    private int threads = 2;

    private EngineHarness(Class<? extends Engine> type) {
        this.type = type;
    }

    /** A harness for one engine class (its descriptor read from the class path). */
    public static EngineHarness of(Class<? extends Engine> type) {
        return new EngineHarness(type);
    }

    public EngineHarness snapshot(String dimension, String value) {
        snapshot.put(dimension, value);
        return this;
    }

    /** The rows of an input (maps of column to value); an input never given is absent. */
    public EngineHarness rows(String input, List<Map<String, Object>> r) {
        rows.put(input, List.copyOf(r));
        return this;
    }

    public EngineHarness option(String name, Object value) {
        options.put(name, value);
        return this;
    }

    public EngineHarness budget(long bytes) {
        this.budget = bytes;
        return this;
    }

    public EngineHarness threads(int n) {
        this.threads = n;
        return this;
    }

    /** Opens the engine and a session over the rows. The caller closes it. */
    public Opened open() throws Exception {
        Path work = Files.createTempDirectory("engine-harness-");
        KitHost host = new KitHost(KitHost.of(type), work, new RachanaEl());
        Map<String, List<Map<String, Object>>> all = new TreeMap<>(rows);
        host.descriptor().inputs().keySet().forEach(i -> all.putIfAbsent(i, null));
        Cases.Case c = new Cases.Case("harness", host.descriptor().id(), null, "harness", "the author's unit test", false,
                new Cases.Given(snapshot, options, all, Map.of(), Map.of()), new Cases.Ask(List.copyOf(host.descriptor().outputs().keySet()), Map.of(), "all", 0),
                Map.of("status", "complete"), null, List.of(), Cases.Laws.ALL, null);
        KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN.threads(threads).budget(budget));
        return new Opened(host, o, c, work);
    }

    /** An open session under test. */
    public static final class Opened implements AutoCloseable {
        private final KitHost host;
        private final KitHost.Opened opened;
        private final Cases.Case c;
        private final Path work;

        Opened(KitHost host, KitHost.Opened opened, Cases.Case c, Path work) {
            this.host = host;
            this.opened = opened;
            this.c = c;
            this.work = work;
        }

        /** Asks {@code outputs} with {@code params}, every row visible. A refusal is thrown as the EngineException it is. */
        public Answer ask(List<String> outputs, Map<String, Object> params) throws Exception {
            return ask(outputs, params, "all");
        }

        /** Asks under a visibility written as a case writes it ({@code all}, or per input {@code {eq: [COL, v]}} and so on). */
        public Answer ask(List<String> outputs, Map<String, Object> params, Object visibility) throws Exception {
            Request r = request(outputs, params, visibility);
            return host.within(() -> opened.session().ask(r, opened.control()));
        }

        /** The explanation of one cell. */
        public Explanation explain(List<String> outputs, Map<String, Object> params, Cell cell) throws Exception {
            Request r = request(outputs, params, "all");
            return host.within(() -> ((Explaining) opened.session()).explain(r, cell, opened.control()));
        }

        /** The workbook's bytes. */
        public byte[] workbook(List<String> outputs, Map<String, Object> params) throws Exception {
            Request r = request(outputs, params, "all");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            host.within(() -> {
                ((Explaining) opened.session()).workbook(r, out, opened.control());
                return null;
            });
            return out.toByteArray();
        }

        /** Applies changes as a case writes them ({@code input}, {@code upserts}, {@code deleted}). */
        public void apply(List<Map<String, Object>> changes) throws EngineException {
            Outcome o = opened.apply(changes);
            if (o != null) {
                throw new EngineException(o.code(), EngineException.Kind.FAILED, o.words());
            }
        }

        /** Bytes the session holds now. */
        public long memoryBytes() {
            return opened.session().memoryBytes();
        }

        private Request request(List<String> outputs, Map<String, Object> params, Object visibility) throws EngineException {
            Cases.Case asked = new Cases.Case(c.suite(), c.engine(), null, c.id(), c.source(), false, c.given(),
                    new Cases.Ask(outputs, params, visibility, 0), c.expect(), null, List.of(), c.laws(), null);
            return KitHost.request(host.descriptor(), asked, KitHost.Variation.PLAIN);
        }

        @Override
        public void close() throws IOException {
            opened.close();
            KitHost.deleteTree(work);
        }
    }

    /** Runs the whole kit on the engine class over a cases folder, as {@code engine tck run} does for a JAR (no delivery group). */
    public static KitRun.Report kit(Class<? extends Engine> type, Path cases, Path out) throws IOException {
        return KitRun.type(type, new KitRun.Settings(cases, out, null, null));
    }

    /** The lines that did not hold, in words: empty when the kit passed. */
    public static List<String> failures(KitRun.Report r) {
        List<String> out = new ArrayList<>();
        r.failures().forEach(f -> out.add(f.group() + " / " + f.subject() + " / " + f.check() + ": " + f.words()));
        return out;
    }
}
