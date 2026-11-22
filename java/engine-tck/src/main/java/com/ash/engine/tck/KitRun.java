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

import com.ash.engine.Descriptor;
import com.ash.engine.Engine;
import com.ash.engine.host.EngineJar;
import com.ash.engine.text.Canonical;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One kit run (section 12.1): for each engine of a JAR (or one engine class), the delivery and scan groups, the descriptor, every case,
 * the laws derived from it, the explanations and workbooks, the lifecycle, the gates; then the whole run once more for the stability gate.
 * Writes {@code results.jsonl}, {@code report.md} and, for a JAR, the certificate beside it.
 */
public final class KitRun {

    /** Where the cases are, where the results go, the previous release's cases (or null), one engine id (or null for all). */
    public record Settings(Path cases, Path out, Path previous, String id) {}

    /** The run of one engine: its lines, counts and verdict. */
    public record Report(String engine, String version, String label, List<Result> results, int cases, ExplainCheck.Counts explain,
            String descriptorHash) {

        /** True when every line held. */
        public boolean passed() {
            return results.stream().allMatch(Result::ok);
        }

        /** The lines that did not hold. */
        public List<Result> failures() {
            return results.stream().filter(r -> !r.ok()).toList();
        }

        /** The law names that ran at least once and never failed. */
        public List<String> lawsPassed() {
            Set<String> ran = new TreeSet<>();
            Set<String> failed = new TreeSet<>();
            for (Result r : results) {
                if (("laws".equals(r.group()) || "explain".equals(r.group()) && "explain".equals(r.check())) && Laws.ALL.contains(r.check())) {
                    ran.add(r.check());
                    if (!r.ok()) {
                        failed.add(r.check());
                    }
                }
            }
            ran.removeAll(failed);
            return Laws.ALL.stream().filter(ran::contains).toList();
        }
    }

    private KitRun() {
    }

    /** Runs the kit on every engine of {@code jar} (or the one named in the settings); writes the files; returns the reports. */
    public static List<Report> jar(Path jar, Settings s) throws IOException {
        List<Report> out = new ArrayList<>();
        try (EngineJar ej = EngineJar.open(jar, KitRun.class.getClassLoader(), Kit.provided())) {
            if (ej.engines().isEmpty()) {
                Report r = new Report("?", "?", jar.getFileName().toString(),
                        ej.problems().stream().map(p -> Result.fail("delivery", jar.getFileName().toString(), "loads", p.toString())).toList(),
                        0, new ExplainCheck.Counts(), null);
                out.add(r);
                write(s, r, jar, ej);
                return out;
            }
            for (EngineJar.Registered reg : ej.engines()) {
                if (reg.descriptor() == null || s.id() != null && !s.id().equals(reg.id())) {
                    continue;
                }
                KitHost.Subject subject = KitHost.of(ej, reg);
                List<Result> delivery = Delivery.check(ej, reg.descriptor(), KitRun.class.getClassLoader());
                Report r = run(subject, s, delivery, ej.loader());
                out.add(r);
                write(s, r, jar, ej);
            }
        }
        return out;
    }

    /** Runs the kit on an engine class on the class path (no JAR: no delivery group, no certificate). */
    public static Report type(Class<? extends Engine> type, Settings s) throws IOException {
        KitHost.Subject subject = KitHost.of(type);
        Report r = run(subject, s, List.of(), type.getClassLoader());
        write(s, r, null, null);
        return r;
    }

    private static Report run(KitHost.Subject subject, Settings s, List<Result> delivery, ClassLoader resources) throws IOException {
        Descriptor d = subject.descriptor();
        List<Cases.Case> cases = Cases.read(s.cases(), d.id());
        Path work = Files.createDirectories(s.out().resolve("work"));
        Report first = once(subject, s, delivery, resources, cases, work);
        Report second = once(subject, s, delivery, resources, cases, work);
        List<Result> all = new ArrayList<>(first.results());
        List<String> a = first.results().stream().map(r -> Canonical.json(r.plain())).toList();
        List<String> b = second.results().stream().map(r -> Canonical.json(r.plain())).toList();
        String diff = null;
        if (!a.equals(b)) {
            for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                if (!a.get(i).equals(b.get(i))) {
                    diff = "line " + (i + 1) + " differs between two runs: " + a.get(i) + " | " + b.get(i);
                    break;
                }
            }
            diff = diff != null ? diff : a.size() + " lines and " + b.size();
        }
        all.add(Result.of("gates", d.id(), "stable", diff, null));
        KitHost.deleteTree(work);
        return new Report(d.id(), d.version(), subject.label(), all, cases.size(), first.explain(), first.descriptorHash());
    }

    private static Report once(KitHost.Subject subject, Settings s, List<Result> delivery, ClassLoader resources, List<Cases.Case> cases,
            Path work) throws IOException {
        Descriptor d = subject.descriptor();
        ElOracle el = new RachanaEl();
        KitHost host = new KitHost(subject, work, el);
        List<Result> out = new ArrayList<>(delivery);
        out.addAll(Checks.descriptor(host));
        Laws laws = new Laws(host);
        ExplainCheck explain = new ExplainCheck(host, el);
        Cases.Case lifecycleCase = null;
        Outcome lifecycleBase = null;
        for (Cases.Case c : cases) {
            Outcome base = host.run(c, KitHost.Variation.PLAIN);
            out.add(Result.of("cases", c.name(), "expect", Compare.expected(c, base, d), base.hash()));
            out.add(Checks.answer(d, c, base));
            out.addAll(laws.run(c, base));
            out.addAll(explain.run(c, base));
            if (lifecycleCase == null && base.answered() && !base.answer().tables().isEmpty()) {
                lifecycleCase = c;
                lifecycleBase = base;
            }
        }
        if (lifecycleCase != null) {
            out.addAll(Checks.lifecycle(host, lifecycleCase, lifecycleBase));
        } else {
            out.add(Result.fail("lifecycle", d.id(), "lifecycle", "no case answers with a table, so the lifecycle cannot be proven"));
        }
        out.addAll(Gates.coverage(host, cases, resources));
        out.addAll(Gates.oracles(d, cases, explain.counts()));
        out.addAll(Gates.additive(d, cases, s.previous()));
        return new Report(d.id(), d.version(), subject.label(), out, cases.size(), explain.counts(),
                Canonical.sha384(Canonical.json(d.raw())));
    }

    private static void write(Settings s, Report r, Path jar, EngineJar ej) throws IOException {
        Path dir = Files.createDirectories(s.out().resolve(r.engine()));
        StringBuilder lines = new StringBuilder();
        for (Result x : r.results()) {
            lines.append(Canonical.json(x.plain())).append('\n');
        }
        Path results = dir.resolve("results.jsonl");
        Files.writeString(results, lines, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("report.md"), report(r), StandardCharsets.UTF_8);
        if (jar != null && ej != null && r.passed()) {
            Map<String, Object> cert = new LinkedHashMap<>();
            cert.put("engine", r.engine());
            cert.put("version", r.version());
            cert.put("jar-sha384", ej.sha384());
            cert.put("descriptor-sha384", r.descriptorHash());
            cert.put("api", com.ash.engine.EngineApi.VERSION);
            cert.put("tck", Kit.version());
            cert.put("bindings", List.of("in-process"));
            cert.put("cases", r.cases());
            cert.put("laws", r.results().stream().filter(x -> "laws".equals(x.group())).count());
            cert.put("laws-passed", r.lawsPassed());
            cert.put("opaque-steps", r.explain().opaque());
            cert.put("results-sha384", Canonical.sha384(lines.toString()));
            cert.put("at", Instant.now().toString());
            Files.writeString(dir.resolve(jar.getFileName() + ".certificate.json"), Canonical.json(cert) + "\n", StandardCharsets.UTF_8);
        }
    }

    /** The human page: one line per group, as the specification's worked example shows, then every failure. */
    static String report(Report r) {
        Map<String, int[]> groups = new LinkedHashMap<>();
        Map<String, int[]> laws = new LinkedHashMap<>();
        for (Result x : r.results()) {
            groups.computeIfAbsent(x.group(), k -> new int[2])[x.ok() ? 0 : 1]++;
            if ("laws".equals(x.group())) {
                laws.computeIfAbsent(x.check(), k -> new int[2])[x.ok() ? 0 : 1]++;
            }
        }
        StringBuilder b = new StringBuilder("<!-- Generated by the Drishti engine kit ").append(Kit.version()).append(". -->\n");
        b.append("# Kit run: ").append(r.label()).append("\n\n");
        b.append(r.passed() ? "**Passed.**" : "**Failed: " + r.failures().size() + " line(s).**").append(" ").append(r.cases()).append(" cases.\n\n");
        b.append("| Group | Held | Failed |\n|---|---|---|\n");
        groups.forEach((g, n) -> b.append("| ").append(g).append(" | ").append(n[0]).append(" | ").append(n[1]).append(" |\n"));
        b.append("\n| Law | Held | Failed |\n|---|---|---|\n");
        laws.forEach((g, n) -> b.append("| ").append(g).append(" | ").append(n[0]).append(" | ").append(n[1]).append(" |\n"));
        b.append("\nExplain: ").append(r.explain().cells()).append(" cells, ").append(r.explain().steps()).append(" steps, ")
                .append(r.explain().opaque()).append(" opaque; workbook names recalculated: ").append(r.explain().namesEqual()).append(" of ")
                .append(r.explain().names()).append(" equal.\n");
        b.append("\nNot checked by this kit: the process binding (law binding; an engine that lists process fails), memoryBytes() against a heap measurement, "
                + "a case file's `file:` inputs, supplier ports and peers.\n");
        if (!r.passed()) {
            b.append("\n## Failures\n\n");
            r.failures().forEach(f -> b.append("- ").append(f.group()).append(" / ").append(f.subject()).append(" / ").append(f.check()).append(": ")
                    .append(f.words()).append('\n'));
        }
        return b.toString();
    }
}
