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
package com.ash.engine;

import com.ash.engine.text.StrictYaml;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reads descriptors strictly (a repeated key, a tag, an unknown key or a value of the wrong type is {@code ENG-1001}, with the place),
 * and states the rules the schema cannot say (section 6.4 of the specification).
 */
public final class Descriptors {

    /** Where a descriptor lives in its JAR: {@code META-INF/drishti-engine/<id>.engine.yaml}. */
    public static final String FOLDER = "META-INF/drishti-engine/";

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?");
    private static final Pattern BUILT = Pattern.compile("\\d+\\.\\d+");
    private static final Pattern CONTRACT = Pattern.compile("[a-z][a-z0-9-]*/[a-z][a-z0-9-]*@\\d+");
    private static final Pattern EXACT = Pattern.compile("bits|text|relative:[0-9.eE-]+");
    private static final Pattern LATENCY = Pattern.compile("(\\d+)(ms|s)");
    private static final Set<String> TYPED = Set.of("text", "int", "number", "decimal", "bool", "date", "enum", "list", "records", "document", "table");
    private static final Set<String> COLUMN_TYPES = Set.of("string", "integer", "number", "decimal", "boolean", "date", "timestamp", "time", "list");
    private static final Set<String> CAPABILITIES = Set.of("explain", "workbook", "incremental", "partition", "process", "supplier", "progressive");

    private Descriptors() {
    }

    /** The descriptor {@code <id>.engine.yaml} read from the JAR (or class path) of {@code owner}; IllegalStateException when absent or invalid. */
    public static Descriptor of(Class<?> owner, String id) {
        String path = FOLDER + id + ".engine.yaml";
        ClassLoader l = owner.getClassLoader();
        try (InputStream in = l == null ? ClassLoader.getSystemResourceAsStream(path) : l.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("ENG-1003 " + id + ": no " + path + " beside " + owner.getSimpleName());
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("ENG-1003 " + id + ": " + path + " cannot be read", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** Reads one descriptor document; IllegalArgumentException whose words start {@code ENG-1001} and name the place. */
    public static Descriptor parse(String yaml) {
        Map<String, Object> m;
        try {
            m = StrictYaml.readMap(yaml);
        } catch (IllegalArgumentException e) {
            throw bad("", e.getMessage());
        }
        return new R(m).descriptor();
    }

    private static IllegalArgumentException bad(String where, String words) {
        return new IllegalArgumentException("ENG-1001 the descriptor is not valid" + (where.isEmpty() ? "" : " at " + where) + ": " + words);
    }

    /**
     * The rules section 6.3 cannot say (section 6.4), as words, one per finding; empty when there is none. Rules 5 and 6 need the engine
     * object and the JAR, so the kit checks them.
     */
    public static List<String> problems(Descriptor d) {
        List<String> out = new ArrayList<>();
        d.outputs().forEach((name, o) -> {
            o.params().stream().filter(p -> !d.params().containsKey(p)).forEach(p -> out.add("outputs." + name + ".params names " + p + ", which params does not declare"));
            o.needs().stream().filter(p -> !d.inputs().containsKey(p)).forEach(p -> out.add("outputs." + name + ".needs names " + p + ", which inputs does not declare"));
            Set<String> cols = new LinkedHashSet<>();
            for (Descriptor.Column c : o.columns()) {
                if (!cols.add(c.name())) {
                    out.add("outputs." + name + ": the column " + c.name() + " is repeated");
                }
                if (c.label() == null || c.label().isBlank()) {
                    out.add("outputs." + name + "." + c.name() + " has no label");
                }
                if (c.measure() && !Set.of("number", "integer", "decimal").contains(c.type())) {
                    out.add("outputs." + name + "." + c.name() + " is a measure of type " + c.type() + ": measures are numbers, integers or decimals");
                }
                if ("decimal".equals(c.type()) && c.exact() != null && !"text".equals(c.exact())) {
                    out.add("outputs." + name + "." + c.name() + ": a decimal is compared as text only");
                }
            }
            for (String k : o.key()) {
                Descriptor.Column c = o.columns().stream().filter(x -> x.name().equals(k)).findFirst().orElse(null);
                if (c == null) {
                    out.add("outputs." + name + ".key names " + k + ", which is not a column");
                } else if (c.nullable() || c.measure()) {
                    out.add("outputs." + name + ".key column " + k + " must be non-nullable and not a measure");
                }
            }
            o.order().stream().filter(k -> !cols.contains(k)).forEach(k -> out.add("outputs." + name + ".order names " + k + ", which is not a column"));
            if (!"row".equals(o.shape()) && o.key().isEmpty()) {
                out.add("outputs." + name + ": a " + o.shape() + " output needs a key");
            }
            if ("series".equals(o.shape()) && (o.axis() == null || !cols.contains(o.axis()))) {
                out.add("outputs." + name + ": a series needs an axis that is one of its columns");
            }
        });
        boolean measures = d.outputs().values().stream().anyMatch(o -> !o.measures().isEmpty());
        if (measures && (!d.has("explain") || !d.has("workbook"))) {
            out.add("capabilities: an output has a measure, so explain and workbook must be listed");
        }
        if (d.has("partition") && d.partition() == null) {
            out.add("partition: the capability partition needs partition: { input, unit }");
        }
        if (d.partition() != null && !d.inputs().containsKey(d.partition().input())) {
            out.add("partition.input names " + d.partition().input() + ", which inputs does not declare");
        }
        d.inputs().forEach((name, in) -> {
            if (in.about() == null || in.about().isBlank()) {
                out.add("inputs." + name + " has no about");
            }
            in.scopeColumns().stream().filter(c -> !in.columns().contains(c)).forEach(c -> out.add("inputs." + name + ".scope-columns names " + c + ", which columns does not list"));
            in.key().stream().filter(c -> !in.columns().contains(c)).forEach(c -> out.add("inputs." + name + ".key names " + c + ", which columns does not list"));
            if ("rows".equals(in.changes()) && !d.has("incremental")) {
                out.add("inputs." + name + " takes changes, so the capability incremental must be listed");
            }
        });
        d.options().forEach((name, t) -> {
            if (!t.hasWords()) {
                out.add("options." + name + " has no label or help");
            }
            if ("document".equals(t.type()) || "table".equals(t.type())) {
                out.add("options." + name + ": an option is never a " + t.type());
            }
        });
        boolean relative = d.outputs().values().stream().flatMap(o -> o.columns().stream())
                .anyMatch(c -> c.exact() != null && c.exact().startsWith("relative:"));
        long forcing = d.options().values().stream().filter(Descriptor.Typed::forcesExactPath).count();
        if (relative && forcing != 1) {
            out.add("a column is exact to a relative tolerance, so exactly one bool option declares forces: exact-path (8.1 rules 4 and 6); there are " + forcing);
        }
        if (!relative && forcing > 0) {
            out.add("an option declares forces: exact-path, but no column is exact to a relative tolerance");
        }
        d.params().forEach((name, t) -> {
            if ("exact-path".equals(t.forces())) {
                out.add("params." + name + ": forces is an option's declaration, never a parameter's");
            }
            if (!t.hasWords()) {
                out.add("params." + name + " has no label or help");
            }
            if (t.overlays() != null) {
                Descriptor.Input in = d.inputs().get(t.overlays());
                if (in == null) {
                    out.add("params." + name + ".overlays names " + t.overlays() + ", which inputs does not declare");
                } else if (!java.util.Objects.equals(in.contract(), t.contract())) {
                    out.add("params." + name + ": a table that overlays " + t.overlays() + " must have its contract " + in.contract());
                }
            }
        });
        return out;
    }

    /** One pass over the document, with the place of every finding. */
    private static final class R {
        private final Map<String, Object> m;

        R(Map<String, Object> m) {
            this.m = m;
        }

        Descriptor descriptor() {
            keys("", m, Set.of("engine", "id", "version", "title", "about", "publisher", "product", "api", "platform", "worker", "codes", "licence",
                    "snapshot", "inputs", "options", "params", "outputs", "capabilities", "partition", "uses", "limits", "aliases", "ui", "entry"),
                    "engine", "id", "version", "title", "about", "publisher", "product", "api", "inputs", "outputs");
            if (m.containsKey("entry")) {
                throw bad("entry", "the key entry is deleted: the class is registered in META-INF/services/com.ash.engine.Engine");
            }
            if (!Long.valueOf(1).equals(m.get("engine"))) {
                throw bad("engine", "must be 1");
            }
            String id = name("id", m.get("id"));
            String version = match("version", text("version", m.get("version")), VERSION, "a semantic version such as 1.0.0");
            Map<String, Object> pub = map("publisher", m.get("publisher"));
            keys("publisher", pub, Set.of("id", "name"), "id", "name");
            Map<String, String> platform = new TreeMap<>();
            if (m.get("platform") != null) {
                Map<String, Object> p = map("platform", m.get("platform"));
                keys("platform", p, Set.of("tools", "lake", "rachana"));
                p.forEach((k, v) -> platform.put(k, match("platform." + k, text("platform." + k, v), BUILT, "major.minor such as \"1.2\"")));
            }
            String codes = optText("codes", m.get("codes"));
            if (codes != null && (!codes.matches("[A-Z]{2,5}") || Set.of("ENG", "DRS", "RCH", "LK").contains(codes))) {
                throw bad("codes", "two to five capital letters, not ENG, DRS, RCH or LK");
            }
            String worker = optText("worker", m.get("worker"));
            if (worker != null && !worker.matches("python:[A-Za-z_][\\w.]*:[A-Za-z_]\\w*")) {
                throw bad("worker", "python:<module>:<Class>");
            }
            String licence = null;
            if (m.get("licence") != null) {
                Map<String, Object> l = map("licence", m.get("licence"));
                keys("licence", l, Set.of("feature"));
                licence = optText("licence.feature", l.get("feature"));
            }
            List<Descriptor.Dimension> dims = new ArrayList<>();
            if (m.get("snapshot") != null) {
                Map<String, Object> s = map("snapshot", m.get("snapshot"));
                keys("snapshot", s, Set.of("dimensions"));
                int i = 0;
                for (Object o : list("snapshot.dimensions", s.get("dimensions"))) {
                    String w = "snapshot.dimensions[" + i++ + "]";
                    Map<String, Object> d = map(w, o);
                    keys(w, d, Set.of("name", "type", "label"), "name", "type");
                    String type = text(w + ".type", d.get("type"));
                    if (!Set.of("date", "text", "int").contains(type)) {
                        throw bad(w + ".type", "date, text or int");
                    }
                    dims.add(new Descriptor.Dimension(name(w + ".name", d.get("name")), type, optText(w + ".label", d.get("label"))));
                }
            }
            Map<String, Descriptor.Input> inputs = new LinkedHashMap<>();
            map("inputs", m.get("inputs") == null ? Map.of() : m.get("inputs")).forEach((k, v) -> inputs.put(name("inputs." + k, k), input("inputs." + k, v)));
            Map<String, Descriptor.Typed> options = typedMap("options", m.get("options"));
            Map<String, Descriptor.Typed> params = typedMap("params", m.get("params"));
            Map<String, Descriptor.Output> outputs = new LinkedHashMap<>();
            Map<String, Object> outs = map("outputs", m.get("outputs"));
            if (outs.isEmpty()) {
                throw bad("outputs", "at least one output");
            }
            outs.forEach((k, v) -> outputs.put(name("outputs." + k, k), output("outputs." + k, v)));
            Set<String> caps = new LinkedHashSet<>();
            if (m.get("capabilities") != null) {
                for (Object c : list("capabilities", m.get("capabilities"))) {
                    String cap = text("capabilities", c);
                    if (!CAPABILITIES.contains(cap)) {
                        throw bad("capabilities", "unknown capability " + cap + "; one of " + new java.util.TreeSet<>(CAPABILITIES));
                    }
                    if (!caps.add(cap)) {
                        throw bad("capabilities", cap + " is listed twice");
                    }
                }
            }
            Descriptor.Partition partition = null;
            if (m.get("partition") != null) {
                Map<String, Object> p = map("partition", m.get("partition"));
                keys("partition", p, Set.of("input", "unit"), "input", "unit");
                partition = new Descriptor.Partition(name("partition.input", p.get("input")), text("partition.unit", p.get("unit")));
            }
            Map<String, Descriptor.Port> uses = new LinkedHashMap<>();
            if (m.get("uses") != null) {
                map("uses", m.get("uses")).forEach((k, v) -> {
                    Map<String, Object> u = map("uses." + k, v);
                    keys("uses." + k, u, Set.of("contract", "about"), "contract", "about");
                    uses.put(k, new Descriptor.Port(contract("uses." + k + ".contract", u.get("contract")), text("uses." + k + ".about", u.get("about"))));
                });
            }
            Descriptor.Limits limits = new Descriptor.Limits(0, Map.of(), Duration.ofSeconds(1));
            if (m.get("limits") != null) {
                Map<String, Object> l = map("limits", m.get("limits"));
                keys("limits", l, Set.of("memory", "cancel-latency"));
                long fixed = 0;
                Map<String, Long> perRow = new TreeMap<>();
                if (l.get("memory") != null) {
                    Map<String, Object> mem = map("limits.memory", l.get("memory"));
                    keys("limits.memory", mem, Set.of("fixed-bytes", "per-row-bytes"));
                    fixed = mem.get("fixed-bytes") == null ? 0 : nonNegative("limits.memory.fixed-bytes", mem.get("fixed-bytes"));
                    if (mem.get("per-row-bytes") != null) {
                        map("limits.memory.per-row-bytes", mem.get("per-row-bytes")).forEach((k, v) -> perRow.put(k, nonNegative("limits.memory.per-row-bytes." + k, v)));
                    }
                }
                Duration latency = Duration.ofSeconds(1);
                if (l.get("cancel-latency") != null) {
                    var mm = LATENCY.matcher(text("limits.cancel-latency", l.get("cancel-latency")));
                    if (!mm.matches()) {
                        throw bad("limits.cancel-latency", "a duration such as 200ms or 1s");
                    }
                    long n = Long.parseLong(mm.group(1));
                    latency = mm.group(2).equals("ms") ? Duration.ofMillis(n) : Duration.ofSeconds(n);
                }
                limits = new Descriptor.Limits(fixed, Collections.unmodifiableMap(perRow), latency);
            }
            Map<String, String> aliases = new LinkedHashMap<>();
            if (m.get("aliases") != null) {
                map("aliases", m.get("aliases")).forEach((k, v) -> aliases.put(k, name("aliases." + k, v)));
            }
            if (m.get("ui") != null) {
                map("ui", m.get("ui"));
            }
            return new Descriptor(1, id, version, text("title", m.get("title")), text("about", m.get("about")),
                    new Descriptor.Publisher(text("publisher.id", pub.get("id")), text("publisher.name", pub.get("name"))), name("product", m.get("product")),
                    match("api", text("api", m.get("api")), BUILT, "major.minor such as \"1.0\" (quoted)"), Collections.unmodifiableMap(platform), worker, codes, licence,
                    List.copyOf(dims), Collections.unmodifiableMap(inputs), options, params, Collections.unmodifiableMap(outputs),
                    Collections.unmodifiableSet(caps), partition, Collections.unmodifiableMap(uses), limits, Collections.unmodifiableMap(aliases),
                    Collections.unmodifiableMap(m));
        }

        private Descriptor.Input input(String w, Object v) {
            Map<String, Object> in = map(w, v);
            keys(w, in, Set.of("contract", "about", "open", "required", "mode", "key", "columns", "vector", "scope-columns", "changes", "ordered-by", "per"), "about");
            boolean open = flag(w + ".open", in.get("open"), false);
            String mode = in.get("mode") == null ? "session" : text(w + ".mode", in.get("mode"));
            if (!Set.of("session", "request").contains(mode)) {
                throw bad(w + ".mode", "session or request");
            }
            if (open) {
                if (!"request".equals(mode)) {
                    throw bad(w, "an open input is read in mode request");
                }
                for (String k : List.of("contract", "columns", "key", "vector", "scope-columns", "changes", "per")) {
                    if (in.containsKey(k)) {
                        throw bad(w + "." + k, "an open input takes its schema from the host's catalogue: no " + k);
                    }
                }
            } else {
                if (!in.containsKey("contract") || !in.containsKey("columns")) {
                    throw bad(w, "contract and columns are required (unless open: true)");
                }
            }
            String changes = in.get("changes") == null ? "none" : text(w + ".changes", in.get("changes"));
            if (!Set.of("none", "rows").contains(changes)) {
                throw bad(w + ".changes", "none or rows");
            }
            List<String> columns = texts(w + ".columns", in.get("columns"));
            if (!open && columns.isEmpty()) {
                throw bad(w + ".columns", "at least one column");
            }
            if (new LinkedHashSet<>(columns).size() != columns.size()) {
                throw bad(w + ".columns", "a column is listed twice");
            }
            return new Descriptor.Input(open ? null : contract(w + ".contract", in.get("contract")), text(w + ".about", in.get("about")), open,
                    flag(w + ".required", in.get("required"), true), mode, texts(w + ".key", in.get("key")), columns,
                    optText(w + ".vector", in.get("vector")), texts(w + ".scope-columns", in.get("scope-columns")), changes,
                    texts(w + ".ordered-by", in.get("ordered-by")), in.containsKey("per") ? texts(w + ".per", in.get("per")) : null);
        }

        private Descriptor.Output output(String w, Object v) {
            Map<String, Object> o = map(w, v);
            keys(w, o, Set.of("title", "about", "shape", "params", "needs", "key", "order", "axis", "columns", "paged", "stochastic", "contract"),
                    "title", "about", "columns");
            String shape = o.get("shape") == null ? "table" : text(w + ".shape", o.get("shape"));
            if (!Set.of("table", "row", "series").contains(shape)) {
                throw bad(w + ".shape", "table, row or series");
            }
            List<Descriptor.Column> cols = new ArrayList<>();
            int i = 0;
            for (Object c : list(w + ".columns", o.get("columns"))) {
                String cw = w + ".columns[" + i++ + "]";
                Map<String, Object> cm = map(cw, c);
                keys(cw, cm, Set.of("name", "type", "nullable", "label", "unit", "format", "scale", "measure", "exact", "passes"), "name", "type");
                String type = text(cw + ".type", cm.get("type"));
                if (!COLUMN_TYPES.contains(type)) {
                    throw bad(cw + ".type", "one of " + new java.util.TreeSet<>(COLUMN_TYPES));
                }
                String exact = optText(cw + ".exact", cm.get("exact"));
                if (exact != null && !EXACT.matcher(exact).matches()) {
                    throw bad(cw + ".exact", "bits, text or relative:<x>");
                }
                cols.add(new Descriptor.Column(text(cw + ".name", cm.get("name")), type, flag(cw + ".nullable", cm.get("nullable"), true),
                        optText(cw + ".label", cm.get("label")), optText(cw + ".unit", cm.get("unit")), optText(cw + ".format", cm.get("format")),
                        cm.get("scale") == null ? null : (int) nonNegative(cw + ".scale", cm.get("scale")), flag(cw + ".measure", cm.get("measure"), false),
                        exact, optText(cw + ".passes", cm.get("passes"))));
            }
            if (cols.isEmpty()) {
                throw bad(w + ".columns", "at least one column");
            }
            return new Descriptor.Output(text(w + ".title", o.get("title")), text(w + ".about", o.get("about")), shape,
                    names(w + ".params", o.get("params")), names(w + ".needs", o.get("needs")), texts(w + ".key", o.get("key")),
                    texts(w + ".order", o.get("order")), optText(w + ".axis", o.get("axis")), List.copyOf(cols),
                    flag(w + ".paged", o.get("paged"), false), flag(w + ".stochastic", o.get("stochastic"), false),
                    o.get("contract") == null ? null : contract(w + ".contract", o.get("contract")));
        }

        private Map<String, Descriptor.Typed> typedMap(String w, Object v) {
            Map<String, Descriptor.Typed> out = new LinkedHashMap<>();
            if (v != null) {
                map(w, v).forEach((k, t) -> out.put(name(w + "." + k, k), typed(w + "." + k, t)));
            }
            return Collections.unmodifiableMap(out);
        }

        @SuppressWarnings("unchecked")
        private Descriptor.Typed typed(String w, Object v) {
            Map<String, Object> t = map(w, v);
            keys(w, t, Set.of("type", "schema", "contract", "max-rows", "overlays", "label", "help", "unit", "default", "required", "min", "max",
                    "allowed", "options", "items", "fields", "forces"), "type");
            String type = text(w + ".type", t.get("type"));
            if (!TYPED.contains(type)) {
                throw bad(w + ".type", "one of " + new java.util.TreeSet<>(TYPED));
            }
            switch (type) {
                case "enum" -> need(w, t, "options");
                case "list" -> need(w, t, "items");
                case "records" -> need(w, t, "fields");
                case "document" -> need(w, t, "schema");
                case "table" -> {
                    need(w, t, "contract");
                    need(w, t, "max-rows");
                }
                default -> {
                    // no further key
                }
            }
            String items = optText(w + ".items", t.get("items"));
            if (items != null && !Set.of("text", "int", "number", "decimal", "bool", "date").contains(items)) {
                throw bad(w + ".items", "text, int, number, decimal, bool or date");
            }
            Map<String, Descriptor.Typed> fields = t.get("fields") == null ? null : typedMap(w + ".fields", t.get("fields"));
            String forces = optText(w + ".forces", t.get("forces"));
            if (forces != null && (!"exact-path".equals(forces) || !"bool".equals(type))) {
                throw bad(w + ".forces", "forces: exact-path, on a bool option only");
            }
            return new Descriptor.Typed(type, t.get("schema") == null ? null : map(w + ".schema", t.get("schema")),
                    t.get("contract") == null ? null : contract(w + ".contract", t.get("contract")),
                    t.get("max-rows") == null ? null : (int) nonNegative(w + ".max-rows", t.get("max-rows")),
                    t.get("overlays") == null ? null : name(w + ".overlays", t.get("overlays")),
                    optText(w + ".label", t.get("label")), optText(w + ".help", t.get("help")), optText(w + ".unit", t.get("unit")),
                    t.get("default"), flag(w + ".required", t.get("required"), false), number(w + ".min", t.get("min")), number(w + ".max", t.get("max")),
                    t.get("allowed") == null ? null : List.copyOf((List<Object>) list(w + ".allowed", t.get("allowed"))),
                    t.get("options") == null ? null : texts(w + ".options", t.get("options")), items, fields, forces);
        }

        private static void need(String w, Map<String, Object> t, String key) {
            if (!t.containsKey(key)) {
                throw bad(w, "a " + t.get("type") + " needs " + key);
            }
        }

        private static void keys(String w, Map<String, Object> m, Set<String> allowed, String... required) {
            for (String k : m.keySet()) {
                if (!allowed.contains(k)) {
                    throw bad(w.isEmpty() ? k : w + "." + k, "unknown key " + k);
                }
            }
            for (String r : required) {
                if (!m.containsKey(r) || m.get(r) == null) {
                    throw bad(w.isEmpty() ? r : w + "." + r, "the key " + r + " is required");
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> map(String w, Object v) {
            if (!(v instanceof Map<?, ?>)) {
                throw bad(w, "a mapping is expected");
            }
            return (Map<String, Object>) v;
        }

        private static List<?> list(String w, Object v) {
            if (!(v instanceof List<?> l)) {
                throw bad(w, "a list is expected");
            }
            return l;
        }

        private static List<String> texts(String w, Object v) {
            if (v == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (Object o : list(w, v)) {
                out.add(text(w, o));
            }
            return List.copyOf(out);
        }

        private static List<String> names(String w, Object v) {
            List<String> out = texts(w, v);
            out.forEach(n -> name(w, n));
            return out;
        }

        private static String text(String w, Object v) {
            if (!(v instanceof String s) || s.isEmpty()) {
                throw bad(w, "a text is expected");
            }
            return s;
        }

        private static String optText(String w, Object v) {
            return v == null ? null : text(w, v);
        }

        private static String name(String w, Object v) {
            return match(w, text(w, v), NAME, "lower-case letters, digits and '-', starting with a letter, at most 64");
        }

        private static String contract(String w, Object v) {
            return match(w, text(w, v), CONTRACT, "<owner>/<name>@<major>");
        }

        private static String match(String w, String s, Pattern p, String what) {
            if (!p.matcher(s).matches()) {
                throw bad(w, "'" + s + "' is not " + what);
            }
            return s;
        }

        private static boolean flag(String w, Object v, boolean dflt) {
            if (v == null) {
                return dflt;
            }
            if (!(v instanceof Boolean b)) {
                throw bad(w, "true or false");
            }
            return b;
        }

        private static long nonNegative(String w, Object v) {
            if (!(v instanceof Long l) || l < 0) {
                throw bad(w, "a whole number, 0 or more");
            }
            return l;
        }

        private static Double number(String w, Object v) {
            if (v == null) {
                return null;
            }
            if (!(v instanceof Number n)) {
                throw bad(w, "a number");
            }
            return n.doubleValue();
        }
    }
}
