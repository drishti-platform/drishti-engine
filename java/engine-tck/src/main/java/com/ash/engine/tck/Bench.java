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

import com.ash.engine.text.Canonical;
import com.ash.engine.text.StrictYaml;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * {@code engine tck bench} (section 12.7): the engine's reference asks (cases marked {@code reference: true}) timed the same way for
 * every engine: session open cold and warm, ask p50 and p99 alone and with 16 callers, bytes allocated per ask, the session's peak
 * {@code memoryBytes()}. With a baseline and a gate file it compares two such files and fails on a breach. Figures belong to the
 * machine that ran them; they are never part of a certificate.
 */
public final class Bench {

    private Bench() {
    }

    /** One line per reference ask, as plain data. */
    public static List<Map<String, Object>> run(KitHost host, List<Cases.Case> cases, int asks) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Cases.Case c : cases) {
            if (!c.reference()) {
                continue;
            }
            long t0 = System.nanoTime();
            double cold;
            double warm;
            long peak;
            double[] alone = new double[asks];
            double[] crowd;
            long allocated;
            try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
                cold = (System.nanoTime() - t0) / 1e6;
                if (!c.changes().isEmpty()) {
                    o.apply(c.changes());
                }
                o.ask();
                long t1 = System.nanoTime();
                try (KitHost.Opened again = host.open(c, KitHost.Variation.PLAIN)) {
                    warm = (System.nanoTime() - t1) / 1e6;
                }
                com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                long a0 = mx.getCurrentThreadAllocatedBytes();
                for (int i = 0; i < asks; i++) {
                    long s = System.nanoTime();
                    o.ask();
                    alone[i] = (System.nanoTime() - s) / 1e6;
                }
                allocated = (mx.getCurrentThreadAllocatedBytes() - a0) / Math.max(1, asks);
                ExecutorService pool = Executors.newFixedThreadPool(16);
                try {
                    List<Future<Double>> fs = new ArrayList<>();
                    for (int i = 0; i < asks; i++) {
                        fs.add(pool.submit(() -> {
                            long s = System.nanoTime();
                            o.ask();
                            return (System.nanoTime() - s) / 1e6;
                        }));
                    }
                    crowd = new double[asks];
                    for (int i = 0; i < asks; i++) {
                        crowd[i] = fs.get(i).get();
                    }
                } finally {
                    pool.shutdownNow();
                }
                peak = host.within(() -> o.session().memoryBytes());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("case", c.name());
            m.put("open-cold-ms", round(cold));
            m.put("open-warm-ms", round(warm));
            m.put("ask-p50-ms", round(pct(alone, 0.50)));
            m.put("ask-p99-ms", round(pct(alone, 0.99)));
            m.put("ask-p99-16-callers-ms", round(pct(crowd, 0.99)));
            m.put("bytes-per-ask", allocated);
            m.put("peak-memory-bytes", peak);
            m.put("asks", asks);
            out.add(m);
        }
        return out;
    }

    /** Writes the lines as JSON lines. */
    public static void write(Path file, List<Map<String, Object>> lines) throws IOException {
        StringBuilder b = new StringBuilder();
        lines.forEach(l -> b.append(Canonical.json(l)).append('\n'));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, b, StandardCharsets.UTF_8);
    }

    /**
     * Compares {@code now} with {@code baseline} under {@code gate} (a YAML mapping of measure to the allowed growth in percent, such as
     * {@code ask-p50-ms: 5}). The breaches in words; empty when none.
     */
    @SuppressWarnings("unchecked")
    public static List<String> compare(Path baseline, List<Map<String, Object>> now, Path gate) throws IOException {
        Map<String, Object> limits = StrictYaml.readMap(Files.readString(gate, StandardCharsets.UTF_8));
        Map<String, Map<String, Object>> base = new LinkedHashMap<>();
        for (String l : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
            if (!l.isBlank()) {
                Map<String, Object> m = (Map<String, Object>) StrictYaml.read(l);
                base.put(String.valueOf(m.get("case")), m);
            }
        }
        List<String> out = new ArrayList<>();
        for (Map<String, Object> m : now) {
            Map<String, Object> b = base.get(String.valueOf(m.get("case")));
            if (b == null) {
                continue;
            }
            limits.forEach((measure, pct) -> {
                double was = ((Number) b.get(measure)).doubleValue();
                double is = ((Number) m.get(measure)).doubleValue();
                double allowed = was * (1 + ((Number) pct).doubleValue() / 100);
                if (is > allowed && is - was > 1e-9) {
                    out.add(m.get("case") + " " + measure + ": " + is + " against " + was + " (more than " + pct + "%)");
                }
            });
        }
        return out;
    }

    private static double pct(double[] v, double p) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s.length == 0 ? 0 : s[Math.min(s.length - 1, (int) Math.ceil(p * s.length) - 1)];
    }

    private static double round(double ms) {
        return Math.round(ms * 1000) / 1000.0;
    }
}
