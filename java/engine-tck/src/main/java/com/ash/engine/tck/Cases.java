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
import com.ash.engine.Filter;
import com.ash.engine.text.Canonical;
import com.ash.engine.text.StrictYaml;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Kit case files ({@code tck/cases/<engine>/<suite>.yaml}, section 12.2 of the specification) read strictly into {@link Case}s: the
 * suite's {@code given} merged under each case's own.
 */
public final class Cases {

    private Cases() {
    }

    /** One case, ready to run. {@code given}: merged. {@code expect}: as written (plain data). */
    public record Case(String suite, String engine, String since, String id, String source, boolean reference, Given given, Ask request,
            Map<String, Object> expect, List<Map<String, Object>> explain, List<Map<String, Object>> changes, Laws laws, Path file) {

        /** {@code suite/id}: the case's name in results. */
        public String name() {
            return suite + "#" + id;
        }

        /** {@code complete}, {@code partial}, {@code not-found} or {@code refused}. */
        public String expected() {
            if (expect.containsKey("refused")) {
                return "refused";
            }
            return String.valueOf(expect.get("status"));
        }
    }

    /** The inputs and options a case runs over. {@code rows}: per input (null: absent). {@code openTables}: per open input. */
    public record Given(Map<String, String> snapshot, Map<String, Object> options, Map<String, List<Map<String, Object>>> rows,
            Map<String, Map<String, List<Map<String, Object>>>> openTables, Map<String, Object> suppliers) {}

    /** The request of a case. {@code visibility}: {@code all}, or per input a filter as written. */
    public record Ask(List<String> outputs, Map<String, Object> params, Object visibility, long seed) {}

    /** Which laws apply: all, a list, or all but a skipped list with the reason. */
    public record Laws(Set<String> only, Set<String> skip, String why) {

        static final Laws ALL = new Laws(null, Set.of(), null);

        /** True when the law runs for this case. */
        public boolean runs(String law) {
            return (only == null || only.contains(law)) && !skip.contains(law);
        }
    }

    /** Every case of every {@code .yaml} under {@code dir} (sorted by path), for engine {@code id} (null: every engine). */
    public static List<Case> read(Path dir, String id) throws IOException {
        List<Case> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".yaml")).sorted().toList()) {
                List<Case> file = parse(Files.readString(p, StandardCharsets.UTF_8), p);
                for (Case c : file) {
                    if (id == null || id.equals(c.engine())) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    /** The cases of one file's text. IllegalArgumentException naming the file and the place. */
    @SuppressWarnings("unchecked")
    public static List<Case> parse(String yaml, Path file) {
        String where = file == null ? "case file" : file.getFileName().toString();
        Map<String, Object> doc;
        try {
            doc = StrictYaml.readMap(yaml);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
        keys(where, doc, Set.of("suite", "engine", "since", "description", "given", "cases"), "suite", "engine", "cases");
        String suite = String.valueOf(doc.get("suite"));
        String engine = String.valueOf(doc.get("engine"));
        String since = doc.get("since") == null ? null : String.valueOf(doc.get("since"));
        Map<String, Object> shared = doc.get("given") == null ? Map.of() : map(where + " given", doc.get("given"));
        List<Case> out = new ArrayList<>();
        Set<String> ids = new TreeSet<>();
        for (Object o : (List<Object>) doc.get("cases")) {
            Map<String, Object> c = map(where + " case", o);
            String id = String.valueOf(c.get("id"));
            String w = where + " case " + id;
            keys(w, c, Set.of("id", "source", "reference", "given", "request", "expect", "explain", "changes", "laws"), "id", "request", "expect");
            if (!ids.add(id)) {
                throw new IllegalArgumentException(w + ": the id is repeated");
            }
            Map<String, Object> g = merge(shared, c.get("given") == null ? Map.of() : map(w + " given", c.get("given")));
            Map<String, Object> r = map(w + " request", c.get("request"));
            keys(w + " request", r, Set.of("outputs", "params", "visibility", "seed"), "outputs");
            Ask ask = new Ask(List.copyOf((List<String>) r.get("outputs")), r.get("params") == null ? Map.of() : map(w + " params", r.get("params")),
                    r.get("visibility") == null ? "all" : r.get("visibility"), r.get("seed") == null ? 0 : ((Number) r.get("seed")).longValue());
            Map<String, Object> expect = map(w + " expect", c.get("expect"));
            keys(w + " expect", expect, Set.of("status", "tables", "missing", "refused"));
            if (expect.containsKey("refused") == expect.containsKey("status")) {
                throw new IllegalArgumentException(w + ": expect has exactly one of status and refused");
            }
            out.add(new Case(suite, engine, since, id, c.get("source") == null ? null : String.valueOf(c.get("source")),
                    Boolean.TRUE.equals(c.get("reference")), given(w, g), ask, expect,
                    c.get("explain") == null ? null : (List<Map<String, Object>>) c.get("explain"),
                    c.get("changes") == null ? List.of() : (List<Map<String, Object>>) c.get("changes"), laws(w, c.get("laws")), file));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Given given(String w, Map<String, Object> g) {
        keys(w + " given", g, Set.of("snapshot", "options", "inputs", "suppliers"));
        Map<String, String> snapshot = new TreeMap<>();
        if (g.get("snapshot") != null) {
            map(w + " snapshot", g.get("snapshot")).forEach((k, v) -> snapshot.put(k, String.valueOf(v)));
        }
        Map<String, List<Map<String, Object>>> rows = new TreeMap<>();
        Map<String, Map<String, List<Map<String, Object>>>> open = new TreeMap<>();
        if (g.get("inputs") != null) {
            map(w + " inputs", g.get("inputs")).forEach((name, v) -> {
                if (v == null || "absent".equals(v)) {
                    rows.put(name, null);
                    return;
                }
                Map<String, Object> in = map(w + " inputs." + name, v);
                keys(w + " inputs." + name, in, Set.of("rows", "file", "tables"));
                if (in.containsKey("tables")) {
                    Map<String, List<Map<String, Object>>> ts = new TreeMap<>();
                    map(w + " tables", in.get("tables")).forEach((t, tv) -> ts.put(t, (List<Map<String, Object>>) map(w + " " + t, tv).get("rows")));
                    open.put(name, ts);
                } else if (in.containsKey("file")) {
                    throw new IllegalArgumentException(w + " inputs." + name + ": file: is read by kit 0.2; give rows: here");
                } else {
                    rows.put(name, in.get("rows") == null ? List.of() : List.copyOf((List<Map<String, Object>>) in.get("rows")));
                }
            });
        }
        return new Given(snapshot, g.get("options") == null ? Map.of() : map(w + " options", g.get("options")), rows, open,
                g.get("suppliers") == null ? Map.of() : map(w + " suppliers", g.get("suppliers")));
    }

    @SuppressWarnings("unchecked")
    private static Laws laws(String w, Object v) {
        if (v == null || "all".equals(v)) {
            return Laws.ALL;
        }
        if (v instanceof List<?> l) {
            return new Laws(new TreeSet<>((List<String>) l), Set.of(), null);
        }
        Map<String, Object> m = map(w + " laws", v);
        keys(w + " laws", m, Set.of("skip", "why"), "skip", "why");
        return new Laws(null, new TreeSet<>((List<String>) m.get("skip")), String.valueOf(m.get("why")));
    }

    /** A filter as a case writes it: {@code {eq: [COL, v]}}, {@code {in: [COL, [..]]}}, {@code {range: [COL, from, to]}}, {@code and}, {@code or}, {@code not}. */
    @SuppressWarnings("unchecked")
    public static Filter filter(Object v) {
        if ("all".equals(v)) {
            return Filter.ALL;
        }
        Map<String, Object> m = map("filter", v);
        if (m.size() != 1) {
            throw new IllegalArgumentException("a filter has one key: " + m);
        }
        Map.Entry<String, Object> e = m.entrySet().iterator().next();
        List<Object> a = e.getValue() instanceof List<?> l ? (List<Object>) l : List.of(e.getValue());
        return switch (e.getKey()) {
            case "eq" -> new Filter.Eq(String.valueOf(a.get(0)), RowInputs.text(a.get(1)));
            case "in" -> new Filter.In(String.valueOf(a.get(0)), new TreeSet<>(((List<Object>) a.get(1)).stream().map(RowInputs::text).toList()));
            case "range" -> new Filter.Range(String.valueOf(a.get(0)), RowInputs.text(a.get(1)), RowInputs.text(a.get(2)));
            case "and" -> new Filter.And(a.stream().map(Cases::filter).toList());
            case "or" -> new Filter.Or(a.stream().map(Cases::filter).toList());
            case "not" -> new Filter.Not(filter(e.getValue()));
            default -> throw new IllegalArgumentException("not a filter: " + e.getKey());
        };
    }

    /** A visibility's signature: {@code all}, or the hash of its canonical form. */
    public static String signature(Object visibility) {
        return "all".equals(visibility) ? "all" : "v:" + Canonical.sha384(Canonical.json(visibility)).substring(0, 24);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(String w, Object v) {
        if (!(v instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(w + ": a mapping is expected");
        }
        return (Map<String, Object>) v;
    }

    private static void keys(String w, Map<String, Object> m, Set<String> allowed, String... required) {
        for (String k : m.keySet()) {
            if (!allowed.contains(k)) {
                throw new IllegalArgumentException(w + ": unknown key " + k + "; the keys are " + new TreeSet<>(allowed));
            }
        }
        for (String r : required) {
            if (!m.containsKey(r)) {
                throw new IllegalArgumentException(w + ": the key " + r + " is required");
            }
        }
    }

    /** {@code over} merged onto {@code base}: mappings key by key, the inputs block input by input, anything else replaced. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> over) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        over.forEach((k, v) -> {
            Object b = out.get(k);
            if (b instanceof Map<?, ?> bm && v instanceof Map<?, ?> vm) {
                out.put(k, merge((Map<String, Object>) bm, (Map<String, Object>) vm));
            } else {
                out.put(k, v);
            }
        });
        return out;
    }
}
