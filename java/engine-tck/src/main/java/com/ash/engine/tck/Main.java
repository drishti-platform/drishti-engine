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

import com.ash.engine.host.EngineJar;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The kit's command, with a JDK and nothing else:
 *
 * <pre>
 * java -jar engine-tck-&lt;v&gt;-cli.jar tck run   --engine &lt;jar&gt; [--id &lt;engine&gt;] --cases &lt;dir&gt; --out &lt;dir&gt; [--previous &lt;dir&gt;]
 * java -jar engine-tck-&lt;v&gt;-cli.jar tck bench --engine &lt;jar&gt; [--id &lt;engine&gt;] --cases &lt;dir&gt; --out &lt;file&gt; [--asks 200] [--baseline &lt;file&gt; --gate &lt;file&gt;]
 * </pre>
 *
 * Exit status 0 only when every line held (run) or no gate was breached (bench); 2 for a command that cannot be read.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(run(args));
    }

    /** The command's exit status. */
    public static int run(String[] args) throws Exception {
        List<String> a = new ArrayList<>(List.of(args));
        if (!a.isEmpty() && a.get(0).equals("engine")) {
            a.remove(0);
        }
        if (a.size() < 2 || !a.get(0).equals("tck") || !List.of("run", "bench").contains(a.get(1))) {
            System.err.println("usage: engine tck run --engine <jar> [--id <engine>] --cases <dir> --out <dir> [--previous <dir>] [--binding in-process]\n"
                    + "       engine tck bench --engine <jar> [--id <engine>] --cases <dir> --out <file> [--asks 200] [--baseline <file> --gate <file>]");
            return 2;
        }
        Map<String, String> o = new LinkedHashMap<>();
        for (int i = 2; i < a.size(); i++) {
            if (!a.get(i).startsWith("--") || i + 1 >= a.size()) {
                System.err.println("not an option with a value: " + a.get(i));
                return 2;
            }
            o.put(a.get(i).substring(2), a.get(++i));
        }
        for (String need : List.of("engine", "cases", "out")) {
            if (!o.containsKey(need)) {
                System.err.println("--" + need + " is required");
                return 2;
            }
        }
        if (o.containsKey("binding") && !"in-process".equals(o.get("binding"))) {
            System.err.println("this kit has the in-process binding only");
            return 2;
        }
        Path jar = Path.of(o.get("engine")).toAbsolutePath();
        Path cases = Path.of(o.get("cases")).toAbsolutePath();
        if (!Files.isRegularFile(jar) || !Files.isDirectory(cases)) {
            System.err.println((Files.isRegularFile(jar) ? cases + " is not a folder" : jar + " is not a file"));
            return 2;
        }
        if (a.get(1).equals("run")) {
            Path out = Path.of(o.get("out")).toAbsolutePath();
            List<KitRun.Report> reports = KitRun.jar(jar, new KitRun.Settings(cases, out, o.containsKey("previous") ? Path.of(o.get("previous")) : null, o.get("id")));
            boolean ok = !reports.isEmpty();
            for (KitRun.Report r : reports) {
                System.out.print(KitRun.report(r));
                System.out.println("results: " + out.resolve(r.engine()).resolve("results.jsonl"));
                ok &= r.passed();
            }
            return ok ? 0 : 1;
        }
        Path file = Path.of(o.get("out")).toAbsolutePath();
        int asks = Integer.parseInt(o.getOrDefault("asks", "200"));
        try (EngineJar ej = EngineJar.open(jar, Main.class.getClassLoader(), Kit.provided())) {
            List<java.util.Map<String, Object>> lines = new ArrayList<>();
            Path work = Files.createDirectories(file.getParent().resolve("bench-work"));
            for (EngineJar.Registered r : ej.engines()) {
                if (r.descriptor() == null || o.containsKey("id") && !o.get("id").equals(r.id())) {
                    continue;
                }
                KitHost host = new KitHost(KitHost.of(ej, r), work, new RachanaEl());
                lines.addAll(Bench.run(host, Cases.read(cases, r.id()), asks));
            }
            KitHost.deleteTree(work);
            Bench.write(file, lines);
            lines.forEach(System.out::println);
            if (o.containsKey("baseline")) {
                List<String> breaches = Bench.compare(Path.of(o.get("baseline")), lines, Path.of(o.get("gate")));
                breaches.forEach(System.out::println);
                return breaches.isEmpty() ? 0 : 1;
            }
            return 0;
        }
    }
}
