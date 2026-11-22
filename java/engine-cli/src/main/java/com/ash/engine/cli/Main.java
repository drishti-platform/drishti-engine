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
package com.ash.engine.cli;

import com.ash.engine.Answer;
import com.ash.engine.Cell;
import com.ash.engine.ColumnSchema;
import com.ash.engine.Descriptor;
import com.ash.engine.EngineApi;
import com.ash.engine.EngineException;
import com.ash.engine.Explaining;
import com.ash.engine.Explanation;
import com.ash.engine.Filter;
import com.ash.engine.Missing;
import com.ash.engine.Request;
import com.ash.engine.RequestClass;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import com.ash.engine.Step;
import com.ash.engine.Table;
import com.ash.engine.Visibility;
import com.ash.engine.host.Budget;
import com.ash.engine.host.EngineInstance;
import com.ash.engine.host.EngineJar;
import com.ash.engine.host.HostContext;
import com.ash.engine.host.HostControl;
import com.ash.engine.host.Params;
import com.ash.engine.host.Provided;
import com.ash.engine.inputs.JsonlFiles;
import com.ash.engine.inputs.RowInputs;
import com.ash.engine.text.Canonical;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code engine run}: any engine JAR over a folder of JSONL files, with no host.
 *
 * <pre>
 * java -jar engine-cli-&lt;v&gt;-cli.jar run --engine &lt;jar&gt; [--id &lt;engine&gt;] --inputs &lt;dir&gt; --outputs a,b --out &lt;dir&gt;
 *     [--snapshot date=2026-01-13] [--param name=value]... [--option name=value]... [--visibility all|input:COLUMN=value]
 *     [--workbook] [--explain output:COLUMN:KEY=value[,KEY=value]] [--threads 2] [--memory-mb 1024]
 * </pre>
 *
 * Writes {@code <output>.jsonl} per table (one object per row), {@code answer.json} (status, missing inputs in words, stamp), and on
 * request {@code <id>.xlsx} and {@code explain.json}. Exit status 0 for a complete answer, 1 for a partial or not-found one, 3 for a
 * refusal (the words on standard error), 2 for a command that cannot be read.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(args, System.out, System.err));
    }

    /** The command's exit status. */
    public static int run(String[] args, PrintStream out, PrintStream err) throws Exception {
        List<String> a = new ArrayList<>(List.of(args));
        if (!a.isEmpty() && a.get(0).equals("engine")) {
            a.remove(0);
        }
        if (a.isEmpty() || !a.get(0).equals("run")) {
            err.println("usage: engine run --engine <jar> [--id <engine>] --inputs <dir> --outputs a,b --out <dir> [--snapshot k=v] [--param k=v]..."
                    + " [--option k=v]... [--visibility all|input:COLUMN=value] [--workbook] [--explain output:COLUMN:KEY=value] [--threads n] [--memory-mb n]");
            return 2;
        }
        Map<String, String> one = new LinkedHashMap<>();
        Map<String, String> params = new LinkedHashMap<>();
        Map<String, String> options = new LinkedHashMap<>();
        Map<String, String> snapshot = new TreeMap<>();
        boolean workbook = false;
        for (int i = 1; i < a.size(); i++) {
            String k = a.get(i);
            if (k.equals("--workbook")) {
                workbook = true;
                continue;
            }
            if (!k.startsWith("--") || i + 1 >= a.size()) {
                err.println("not an option with a value: " + k);
                return 2;
            }
            String v = a.get(++i);
            switch (k) {
                case "--param" -> put(params, v);
                case "--option" -> put(options, v);
                case "--snapshot" -> put(snapshot, v);
                default -> one.put(k.substring(2), v);
            }
        }
        for (String need : List.of("engine", "inputs", "outputs", "out")) {
            if (!one.containsKey(need)) {
                err.println("--" + need + " is required");
                return 2;
            }
        }
        Path jar = Path.of(one.get("engine")).toAbsolutePath();
        Path outDir = Files.createDirectories(Path.of(one.get("out")).toAbsolutePath());
        try (EngineJar ej = EngineJar.open(jar, Main.class.getClassLoader(), provided())) {
            EngineJar.Registered r = ej.engines().stream().filter(x -> x.descriptor() != null && (one.get("id") == null || one.get("id").equals(x.id())))
                    .findFirst().orElse(null);
            if (r == null) {
                err.println(jar.getFileName() + " registers no engine" + (one.containsKey("id") ? " " + one.get("id") : "") + ": "
                        + ej.problems() + " " + ej.engines().stream().map(EngineJar.Registered::id).toList());
                return 3;
            }
            if (!r.ok()) {
                r.problems().forEach(p -> err.println(p));
                return 3;
            }
            Descriptor d = r.descriptor();
            Map<String, List<Map<String, Object>>> rows = JsonlFiles.read(d, Path.of(one.get("inputs")), Map.of());
            RowInputs inputs = new RowInputs(d, rows, Map.of(), RowInputs.Delivery.PLAIN.withoutPushdown(), new RowInputs.Recorder(), null);
            Path work = Files.createDirectories(outDir.resolve("work"));
            ExecutorService ex = Executors.newFixedThreadPool(Integer.parseInt(one.getOrDefault("threads", "2")));
            Snapshot snap = Snapshot.of(snapshot);
            Instant now = snapshot.values().stream().filter(s -> s.matches("\\d{4}-\\d{2}-\\d{2}")).findFirst()
                    .map(s -> Instant.parse(s + "T12:00:00Z")).orElse(Instant.now());
            try {
                HostContext ctx = new HostContext(d, Params.options(d, new LinkedHashMap<>(options)), work, Clock.fixed(now, ZoneOffset.UTC), ex,
                        new Budget(Long.parseLong(one.getOrDefault("memory-mb", "1024")) << 20), Map.of(), s -> Optional.empty(), s -> inputs.stamps());
                try (EngineInstance inst = new EngineInstance(d.id(), ej.create(r), ej.loader())) {
                    inst.boot(ctx);
                    Session session = inst.session(snap, inputs, HostControl.open());
                    try {
                        List<String> outputs = List.of(one.get("outputs").split(","));
                        Request req = new Request("cli", outputs, Params.request(d, outputs, new LinkedHashMap<>(params)),
                                visibility(d, one.getOrDefault("visibility", "all")), "cli", 0, RequestClass.BATCH);
                        Answer ans = inst.request(() -> session.ask(req, HostControl.open()));
                        write(outDir, d, ans);
                        if (workbook && session instanceof Explaining x) {
                            try (OutputStream o = Files.newOutputStream(outDir.resolve(d.id() + ".xlsx"))) {
                                inst.request(() -> {
                                    x.workbook(req, o, HostControl.open());
                                    return null;
                                });
                            }
                        }
                        if (one.containsKey("explain") && session instanceof Explaining x) {
                            Cell cell = cell(one.get("explain"));
                            Explanation e = inst.request(() -> x.explain(req, cell, HostControl.open()));
                            Files.writeString(outDir.resolve("explain.json"), Canonical.json(plain(e)) + "\n", StandardCharsets.UTF_8);
                        }
                        out.println(ans.status() + " " + ans.tables().stream().map(t -> t.schema().name() + " " + t.rows() + " rows").toList());
                        ans.missing().forEach(m -> out.println(m.words()));
                        return ans.status() == com.ash.engine.Status.COMPLETE ? 0 : 1;
                    } finally {
                        inst.within(() -> {
                            session.close();
                            return null;
                        });
                    }
                }
            } catch (EngineException e) {
                err.println(e.code() + " " + e.getMessage());
                return 3;
            } finally {
                ex.shutdownNow();
            }
        }
    }

    private static void put(Map<String, String> m, String kv) {
        int eq = kv.indexOf('=');
        if (eq <= 0) {
            throw new IllegalArgumentException("name=value expected: " + kv);
        }
        m.put(kv.substring(0, eq), kv.substring(eq + 1));
    }

    /** {@code all}, or {@code input:COLUMN=value} (the rows a request may read): every input with scope columns gets a filter. */
    static Visibility visibility(Descriptor d, String spec) {
        Map<String, Filter> rows = new TreeMap<>();
        d.inputs().forEach((n, in) -> {
            if (!in.scopeColumns().isEmpty()) {
                rows.put(n, Filter.ALL);
            }
        });
        if (!"all".equals(spec)) {
            String[] p = spec.split("[:=]", 3);
            rows.put(p[0], new Filter.Eq(p[1], p[2]));
        }
        return new Visibility(spec, rows, java.util.Set.of());
    }

    static Cell cell(String spec) {
        String[] p = spec.split(":", 3);
        SortedMap<String, String> key = new TreeMap<>();
        if (p.length > 2) {
            for (String kv : p[2].split(",")) {
                key.put(kv.substring(0, kv.indexOf('=')), kv.substring(kv.indexOf('=') + 1));
            }
        }
        return new Cell(p[0], p[1], key);
    }

    private static void write(Path dir, Descriptor d, Answer a) throws IOException {
        for (Table t : a.tables()) {
            StringBuilder b = new StringBuilder();
            for (int r = 0; r < t.rows(); r++) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (ColumnSchema c : t.schema().columns()) {
                    row.put(c.name(), t.value(c.name(), r));
                }
                b.append(Canonical.json(row)).append('\n');
            }
            Files.writeString(dir.resolve(t.schema().name() + ".jsonl"), b, StandardCharsets.UTF_8);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine", d.id());
        m.put("version", d.version());
        m.put("status", a.status().name());
        m.put("missing", a.missing().stream().map(Missing::words).toList());
        m.put("notes", a.notes());
        m.put("stamp", a.stamp() == null ? null : a.stamp().text());
        Files.writeString(dir.resolve("answer.json"), Canonical.json(m) + "\n", StandardCharsets.UTF_8);
    }

    private static Map<String, Object> plain(Explanation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cell", Map.of("output", e.cell().output(), "column", e.cell().column(), "key", e.cell().key()));
        m.put("value", e.value());
        List<Object> steps = new ArrayList<>();
        for (Step s : e.steps()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", s.name());
            x.put("label", s.label());
            x.put("formula", s.formula());
            x.put("value", s.value());
            x.put("opaque", s.opaque());
            steps.add(x);
        }
        m.put("steps", steps);
        return m;
    }

    /** The engine API, and the drishti-tools version on this command's class path. */
    static Provided provided() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Main.class.getResourceAsStream("cli.properties")) {
            if (in != null) {
                p.load(in);
            }
        }
        String tools = p.getProperty("tools");
        return new Provided(EngineApi.VERSION, tools == null || tools.startsWith("${") ? null : tools, null, null);
    }
}
