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

import com.ash.engine.Descriptor;
import com.ash.engine.EngineException;
import com.ash.engine.text.StrictYaml;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A host's engine file (specification section 9): {@code config/engines/<id>.yaml}, the value of {@code drishti.engines.<id>}. It names no
 * JAR and no class. Read strictly: an unknown key or a value of the wrong type is {@code ENG-1010} with the key. {@link #check} then holds
 * it against the installed descriptor (names of inputs, outputs, asks, ports; options through {@link Params#options}).
 */
public record EngineFile(String id, boolean enabled, Map<String, Object> changed, Pin pin, SnapshotPolicy snapshot, Map<String, Binding> inputs,
        Map<String, Object> options, List<Trigger> triggers, Map<String, NamedAsk> asks, Map<String, OutputUse> outputs,
        Map<String, PortUse> uses, Access access, Limits limits, Map<String, Object> placement) {

    /** {@code engine:}: an exact version and the JAR's SHA-384 (both required under trust {@code pinned}); how it runs. */
    public record Pin(String version, String sha384, String run) {}

    /** Sessions kept open ({@code hot}), closed when idle ({@code idle-close}), pinned snapshots. */
    public record SnapshotPolicy(int hot, String idleClose, Map<String, Object> pinned) {}

    /** What binding by contract leaves open: kinds or a table, a column map (contract column to table column), a row filter in Rachana-EL. */
    public record Binding(List<String> kinds, String table, Map<String, String> columns, String where) {}

    /** One trigger: {@code request}, {@code schedule}, {@code data-version}, {@code changes} or {@code after}, with its keys. */
    public record Trigger(String on, String cron, String zone, String every, String input, List<String> inputs, String from, String engine,
            String output, String ask, String quiet, String maxWait) {}

    /** A named request a trigger runs: outputs, parameters, snapshot, the identity it runs as. */
    public record NamedAsk(List<String> outputs, Map<String, Object> params, Map<String, Object> snapshot, String as) {}

    /** What the host does with one output: switched, served, live, stored, who may ask. */
    public record OutputUse(boolean enabled, List<String> serve, boolean live, Store store, Map<String, Object> access) {}

    /** Where a stored output goes: a lake, a table, {@code by-snapshot} or {@code append}. */
    public record Store(String lake, String table, String mode) {}

    /** A {@code uses} port bound to another engine's output. */
    public record PortUse(String engine, String output) {}

    /** Who may open the engine, the host rule that makes a visibility, and the isolation ({@code per-viewer}, {@code shared}, or null: decided by the certificate). */
    public record Access(List<String> opens, String visibility, String isolation) {}

    /** Limits the host enforces; 0 means the host's default. */
    public record Limits(long memoryMb, int threads, int requestSeconds, int diskGb, int refusalTtlSeconds, Map<String, Integer> concurrent) {}

    private static final Set<String> TRIGGERS = Set.of("request", "schedule", "data-version", "changes", "after");

    /** Reads the file's text: either the whole file ({@code drishti: engines: <id>: …}) or the value alone. */
    @SuppressWarnings("unchecked")
    public static EngineFile read(String id, String yaml) throws EngineException {
        Map<String, Object> doc;
        try {
            doc = StrictYaml.readMap(yaml);
        } catch (IllegalArgumentException e) {
            throw bad(id, "", e.getMessage());
        }
        Object v = doc;
        if (doc.containsKey("drishti")) {
            Object engines = map(id, "drishti", doc.get("drishti")).get("engines");
            Object mine = engines == null ? null : map(id, "drishti.engines", engines).get(id);
            if (mine == null) {
                throw bad(id, "drishti.engines." + id, "the file holds no drishti.engines." + id);
            }
            v = mine;
        }
        return of(id, map(id, "", v));
    }

    /** The engine file from its value as plain data. */
    public static EngineFile of(String id, Map<String, Object> m) throws EngineException {
        keys(id, "", m, Set.of("enabled", "changed", "engine", "snapshot", "inputs", "options", "triggers", "asks", "outputs", "uses", "access",
                "limits", "placement"), "enabled", "access");
        Pin pin = null;
        if (m.get("engine") != null) {
            Map<String, Object> e = map(id, "engine", m.get("engine"));
            keys(id, "engine", e, Set.of("version", "sha384", "run", "artifact", "allow-uncertified"));
            if (e.containsKey("artifact") || e.containsKey("allow-uncertified")) {
                throw bad(id, "engine", "engine.artifact and engine.allow-uncertified are deleted: the JAR is found in the engine lib folder; a certificate is needed only under trust pinned");
            }
            String sha = text(id, "engine.sha384", e.get("sha384"));
            if (sha != null && !sha.matches("[0-9a-f]{96}")) {
                throw bad(id, "engine.sha384", "96 lower-case hex digits");
            }
            String run = text(id, "engine.run", e.get("run"));
            if (run != null && !Set.of("in-process", "process").contains(run)) {
                throw bad(id, "engine.run", "in-process or process");
            }
            pin = new Pin(text(id, "engine.version", e.get("version")), sha, run == null ? "in-process" : run);
        }
        SnapshotPolicy snap = new SnapshotPolicy(5, null, Map.of());
        if (m.get("snapshot") != null) {
            Map<String, Object> s = map(id, "snapshot", m.get("snapshot"));
            keys(id, "snapshot", s, Set.of("hot", "idle-close", "pinned"));
            snap = new SnapshotPolicy(s.get("hot") == null ? 5 : (int) whole(id, "snapshot.hot", s.get("hot"), 1), text(id, "snapshot.idle-close", s.get("idle-close")),
                    s.get("pinned") == null ? Map.of() : map(id, "snapshot.pinned", s.get("pinned")));
        }
        Map<String, Binding> inputs = new LinkedHashMap<>();
        if (m.get("inputs") != null) {
            for (Map.Entry<String, Object> e : map(id, "inputs", m.get("inputs")).entrySet()) {
                String w = "inputs." + e.getKey();
                Map<String, Object> b = map(id, w, e.getValue());
                keys(id, w, b, Set.of("kinds", "table", "columns", "where"));
                if (b.containsKey("kinds") && b.containsKey("table")) {
                    throw bad(id, w, "kinds or table, not both");
                }
                Map<String, String> cols = new LinkedHashMap<>();
                if (b.get("columns") != null) {
                    map(id, w + ".columns", b.get("columns")).forEach((k, x) -> cols.put(k, String.valueOf(x)));
                }
                inputs.put(e.getKey(), new Binding(texts(id, w + ".kinds", b.get("kinds")), text(id, w + ".table", b.get("table")),
                        Collections.unmodifiableMap(cols), text(id, w + ".where", b.get("where"))));
            }
        }
        List<Trigger> triggers = new ArrayList<>();
        if (m.get("triggers") == null) {
            triggers.add(new Trigger("request", null, null, null, null, List.of(), null, null, null, null, null, null));
        } else {
            int i = 0;
            for (Object o : list(id, "triggers", m.get("triggers"))) {
                String w = "triggers[" + i++ + "]";
                Map<String, Object> t = map(id, w, o);
                keys(id, w, t, Set.of("on", "cron", "zone", "every", "input", "inputs", "from", "engine", "output", "ask", "quiet", "max-wait"), "on");
                String on = text(id, w + ".on", t.get("on"));
                if (!TRIGGERS.contains(on)) {
                    throw bad(id, w + ".on", "one of request, schedule, data-version, changes, after");
                }
                if ("schedule".equals(on) && t.get("cron") == null && t.get("every") == null) {
                    throw bad(id, w, "a schedule needs cron (with zone) or every");
                }
                if ("schedule".equals(on) && t.get("ask") == null) {
                    throw bad(id, w, "a schedule runs a named ask");
                }
                if ("changes".equals(on) && t.get("input") == null) {
                    throw bad(id, w, "changes needs the input they apply to");
                }
                if ("after".equals(on) && (t.get("engine") == null || t.get("output") == null)) {
                    throw bad(id, w, "after needs engine and output");
                }
                triggers.add(new Trigger(on, text(id, w + ".cron", t.get("cron")), text(id, w + ".zone", t.get("zone")), text(id, w + ".every", t.get("every")),
                        text(id, w + ".input", t.get("input")), texts(id, w + ".inputs", t.get("inputs")), text(id, w + ".from", t.get("from")),
                        text(id, w + ".engine", t.get("engine")), text(id, w + ".output", t.get("output")), text(id, w + ".ask", t.get("ask")),
                        text(id, w + ".quiet", t.get("quiet")), text(id, w + ".max-wait", t.get("max-wait"))));
            }
        }
        Map<String, NamedAsk> asks = new LinkedHashMap<>();
        if (m.get("asks") != null) {
            for (Map.Entry<String, Object> e : map(id, "asks", m.get("asks")).entrySet()) {
                String w = "asks." + e.getKey();
                Map<String, Object> a = map(id, w, e.getValue());
                keys(id, w, a, Set.of("outputs", "params", "snapshot", "as"), "outputs");
                asks.put(e.getKey(), new NamedAsk(texts(id, w + ".outputs", a.get("outputs")),
                        a.get("params") == null ? Map.of() : map(id, w + ".params", a.get("params")),
                        a.get("snapshot") == null ? Map.of() : map(id, w + ".snapshot", a.get("snapshot")), text(id, w + ".as", a.get("as"))));
            }
        }
        Map<String, OutputUse> outputs = new LinkedHashMap<>();
        if (m.get("outputs") != null) {
            for (Map.Entry<String, Object> e : map(id, "outputs", m.get("outputs")).entrySet()) {
                String w = "outputs." + e.getKey();
                Map<String, Object> o = map(id, w, e.getValue());
                keys(id, w, o, Set.of("enabled", "serve", "live", "store", "access"));
                List<String> serve = texts(id, w + ".serve", o.get("serve"));
                for (String x : serve) {
                    if (!Set.of("protocol", "flight").contains(x)) {
                        throw bad(id, w + ".serve", "protocol or flight, not " + x);
                    }
                }
                Store store = null;
                if (o.get("store") != null) {
                    Map<String, Object> s = map(id, w + ".store", o.get("store"));
                    keys(id, w + ".store", s, Set.of("lake", "table", "mode"), "lake", "table", "mode");
                    String mode = text(id, w + ".store.mode", s.get("mode"));
                    if (!Set.of("by-snapshot", "append").contains(mode)) {
                        throw bad(id, w + ".store.mode", "by-snapshot or append");
                    }
                    store = new Store(text(id, w + ".store.lake", s.get("lake")), text(id, w + ".store.table", s.get("table")), mode);
                }
                outputs.put(e.getKey(), new OutputUse(flag(id, w + ".enabled", o.get("enabled"), true), serve, flag(id, w + ".live", o.get("live"), false), store,
                        o.get("access") == null ? Map.of() : map(id, w + ".access", o.get("access"))));
            }
        }
        Map<String, PortUse> uses = new LinkedHashMap<>();
        if (m.get("uses") != null) {
            for (Map.Entry<String, Object> e : map(id, "uses", m.get("uses")).entrySet()) {
                Map<String, Object> u = map(id, "uses." + e.getKey(), e.getValue());
                keys(id, "uses." + e.getKey(), u, Set.of("engine", "output"), "engine", "output");
                uses.put(e.getKey(), new PortUse(text(id, "uses.engine", u.get("engine")), text(id, "uses.output", u.get("output"))));
            }
        }
        Map<String, Object> ac = map(id, "access", m.get("access"));
        keys(id, "access", ac, Set.of("opens", "visibility", "isolation"), "visibility");
        String isolation = text(id, "access.isolation", ac.get("isolation"));
        if (isolation != null && !Set.of("per-viewer", "shared").contains(isolation)) {
            throw bad(id, "access.isolation", "per-viewer or shared");
        }
        Access access = new Access(texts(id, "access.opens", ac.get("opens")), text(id, "access.visibility", ac.get("visibility")), isolation);
        Limits limits = new Limits(0, 0, 30, 0, 0, Map.of());
        if (m.get("limits") != null) {
            Map<String, Object> l = map(id, "limits", m.get("limits"));
            keys(id, "limits", l, Set.of("memory-mb", "threads", "request-seconds", "concurrent", "disk-gb", "refusal-ttl-seconds"));
            Map<String, Integer> conc = new TreeMap<>();
            if (l.get("concurrent") != null) {
                for (Map.Entry<String, Object> e : map(id, "limits.concurrent", l.get("concurrent")).entrySet()) {
                    if (!Set.of("interactive", "event", "batch").contains(e.getKey())) {
                        throw bad(id, "limits.concurrent." + e.getKey(), "interactive, event or batch");
                    }
                    conc.put(e.getKey(), (int) whole(id, "limits.concurrent." + e.getKey(), e.getValue(), 1));
                }
            }
            limits = new Limits(whole(id, "limits.memory-mb", l.getOrDefault("memory-mb", 0L), 0), (int) whole(id, "limits.threads", l.getOrDefault("threads", 0L), 0),
                    (int) whole(id, "limits.request-seconds", l.getOrDefault("request-seconds", 30L), 1), (int) whole(id, "limits.disk-gb", l.getOrDefault("disk-gb", 0L), 0),
                    (int) whole(id, "limits.refusal-ttl-seconds", l.getOrDefault("refusal-ttl-seconds", 0L), 0), Collections.unmodifiableMap(conc));
        }
        return new EngineFile(id, enabled(id, m.get("enabled")), m.get("changed") == null ? Map.of() : map(id, "changed", m.get("changed")), pin, snap,
                Collections.unmodifiableMap(inputs), m.get("options") == null ? Map.of() : map(id, "options", m.get("options")), List.copyOf(triggers),
                Collections.unmodifiableMap(asks), Collections.unmodifiableMap(outputs), Collections.unmodifiableMap(uses), access, limits,
                m.get("placement") == null ? Map.of() : map(id, "placement", m.get("placement")));
    }

    /**
     * The file held against the installed descriptor: every input, output, ask output, port and trigger input it names is declared, and
     * the options are valid ({@code ENG-1013}). Each finding is a problem with the key in its words.
     */
    public List<Problem> check(Descriptor d) {
        List<Problem> out = new ArrayList<>();
        inputs.keySet().stream().filter(n -> !d.inputs().containsKey(n))
                .forEach(n -> out.add(new Problem("ENG-1010", id + ": inputs." + n + " is not an input of " + d.id() + " " + d.version() + "; its inputs are " + d.inputs().keySet())));
        outputs.keySet().stream().filter(n -> !d.outputs().containsKey(n))
                .forEach(n -> out.add(new Problem("ENG-1010", id + ": outputs." + n + " is not an output of " + d.id() + " " + d.version() + "; its outputs are " + d.outputs().keySet())));
        asks.forEach((n, a) -> a.outputs().stream().filter(o -> !d.outputs().containsKey(o))
                .forEach(o -> out.add(new Problem("ENG-1010", id + ": asks." + n + " asks " + o + ", which " + d.id() + " does not answer"))));
        uses.keySet().stream().filter(p -> !d.uses().containsKey(p))
                .forEach(p -> out.add(new Problem("ENG-1010", id + ": uses." + p + " is not a port of " + d.id())));
        for (Trigger t : triggers) {
            if (t.ask() != null && !asks.containsKey(t.ask())) {
                out.add(new Problem("ENG-1010", id + ": a " + t.on() + " trigger runs the ask " + t.ask() + ", which asks does not name"));
            }
            List<String> named = new ArrayList<>(t.inputs());
            if (t.input() != null) {
                named.add(t.input());
            }
            named.stream().filter(n -> !d.inputs().containsKey(n))
                    .forEach(n -> out.add(new Problem("ENG-1010", id + ": a " + t.on() + " trigger names the input " + n + ", which " + d.id() + " does not declare")));
            if ("changes".equals(t.on()) && t.input() != null && d.inputs().containsKey(t.input()) && !"rows".equals(d.inputs().get(t.input()).changes())) {
                out.add(new Problem("ENG-1010", id + ": a changes trigger on " + t.input() + ", which does not take changes (changes: rows)"));
            }
        }
        try {
            Params.options(d, options);
        } catch (EngineException e) {
            out.add(new Problem(e.code(), id + ": options: " + e.getMessage()));
        }
        return out;
    }

    private static EngineException bad(String id, String key, String words) {
        return new EngineException("ENG-1010", EngineException.Kind.REFUSED, "config/engines/" + id + ".yaml is not valid"
                + (key.isEmpty() ? "" : " at " + key) + ": " + words);
    }

    private static boolean enabled(String id, Object v) throws EngineException {
        if (v instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(v).strip();
        if (s.startsWith("${") && s.endsWith("}") && s.contains(":")) {
            s = s.substring(s.indexOf(':') + 1, s.length() - 1);          // the host resolves placeholders; the reference reads the default
        }
        if (!Set.of("true", "false").contains(s)) {
            throw bad(id, "enabled", "true or false");
        }
        return Boolean.parseBoolean(s);
    }

    private static void keys(String id, String w, Map<String, Object> m, Set<String> allowed, String... required) throws EngineException {
        for (String k : m.keySet()) {
            if (!allowed.contains(k)) {
                throw bad(id, w.isEmpty() ? k : w + "." + k, "unknown key " + k);
            }
        }
        for (String r : required) {
            if (m.get(r) == null) {
                throw bad(id, w.isEmpty() ? r : w + "." + r, "the key " + r + " is required");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(String id, String w, Object v) throws EngineException {
        if (!(v instanceof Map<?, ?>)) {
            throw bad(id, w, "a mapping is expected");
        }
        return (Map<String, Object>) v;
    }

    private static List<?> list(String id, String w, Object v) throws EngineException {
        if (!(v instanceof List<?> l)) {
            throw bad(id, w, "a list is expected");
        }
        return l;
    }

    private static String text(String id, String w, Object v) throws EngineException {
        if (v == null) {
            return null;
        }
        if (v instanceof Map<?, ?> || v instanceof List<?>) {
            throw bad(id, w, "a text is expected");
        }
        return v.toString();
    }

    private static List<String> texts(String id, String w, Object v) throws EngineException {
        if (v == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list(id, w, v)) {
            out.add(text(id, w, o));
        }
        return List.copyOf(out);
    }

    private static boolean flag(String id, String w, Object v, boolean dflt) throws EngineException {
        if (v == null) {
            return dflt;
        }
        if (!(v instanceof Boolean b)) {
            throw bad(id, w, "true or false");
        }
        return b;
    }

    private static long whole(String id, String w, Object v, long min) throws EngineException {
        if (!(v instanceof Long l) || l < min) {
            throw bad(id, w, "a whole number, " + min + " or more");
        }
        return l;
    }
}
