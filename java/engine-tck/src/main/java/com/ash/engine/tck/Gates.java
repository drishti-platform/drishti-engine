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
import com.ash.engine.text.Canonical;
import com.ash.engine.text.StrictYaml;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** The gates of section 12.5: coverage, additive only, oracles. Stability is the run's own (two runs, one set of lines). */
public final class Gates {

    private Gates() {
    }

    /** Coverage: every output, every enumerated value, every declared input column read, every own code, NOT_FOUND per required input. */
    public static List<Result> coverage(KitHost host, List<Cases.Case> cases, ClassLoader resources) {
        Descriptor d = host.descriptor();
        List<String> gaps = new ArrayList<>();
        Set<String> asked = new TreeSet<>();
        cases.forEach(c -> asked.addAll(c.request().outputs()));
        d.outputs().keySet().stream().filter(o -> !asked.contains(o)).forEach(o -> gaps.add("no case asks the output " + o));
        Set<String> accepted = new TreeSet<>();
        d.outputs().values().forEach(o -> accepted.addAll(o.params()));
        for (String p : accepted) {
            Descriptor.Typed t = d.params().get(p);
            List<Object> values = new ArrayList<>();
            if (t.options() != null) {
                values.addAll(t.options());
            }
            if (t.allowed() != null) {
                values.addAll(t.allowed());
            }
            for (Object v : values) {
                boolean used = cases.stream().anyMatch(c -> {
                    Object given = c.request().params().containsKey(p) ? c.request().params().get(p) : t.defaultValue();
                    boolean takes = c.request().outputs().stream().anyMatch(o -> d.output(o) != null && d.output(o).params().contains(p));
                    return takes && given != null && v.toString().equals(given.toString());
                });
                if (!used) {
                    gaps.add("no case asks " + p + " = " + v);
                }
            }
        }
        d.options().forEach((name, t) -> {
            if (t.options() != null) {
                for (String v : t.options()) {
                    boolean used = cases.stream().anyMatch(c -> v.equals(String.valueOf(c.given().options().getOrDefault(name, t.defaultValue()))));
                    if (!used) {
                        gaps.add("no case runs the option " + name + " = " + v);
                    }
                }
            }
        });
        Set<String> read = host.recorder().read();
        d.inputs().forEach((name, in) -> {
            if (!in.open()) {
                in.columns().stream().filter(c -> !read.contains(name + "." + c)).forEach(c -> gaps.add("no case reads " + name + "." + c));
            }
            if (in.required()) {
                boolean nf = cases.stream().anyMatch(c -> c.given().rows().containsKey(name) && c.given().rows().get(name) == null
                        && Set.of("not-found", "partial").contains(c.expected()));
                if (!nf) {
                    gaps.add("no case gives NOT_FOUND for the required input " + name);
                }
            }
        });
        for (String code : ownCodes(d, resources)) {
            if (cases.stream().noneMatch(c -> code.equals(String.valueOf(c.expect().get("refused"))))) {
                gaps.add("no case expects the engine's own code " + code);
            }
        }
        if (!host.recorder().undeclared().isEmpty()) {
            gaps.add("undeclared columns were asked: " + host.recorder().undeclared());
        }
        return List.of(Result.of("gates", d.id(), "coverage", gaps.isEmpty() ? null : String.join("; ", gaps), null));
    }

    /** The engine's own codes ({@code META-INF/drishti-engine/<id>.codes.yaml}), empty when it declares none. */
    @SuppressWarnings("unchecked")
    static List<String> ownCodes(Descriptor d, ClassLoader resources) {
        if (d.codes() == null || resources == null) {
            return List.of();
        }
        try (InputStream in = resources.getResourceAsStream("META-INF/drishti-engine/" + d.id() + ".codes.yaml")) {
            if (in == null) {
                return List.of("(" + d.id() + ".codes.yaml is missing)");
            }
            Object doc = StrictYaml.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) ((Map<String, Object>) doc).get("codes")) {
                out.add(String.valueOf(((Map<String, Object>) o).get("code")));
            }
            return out;
        } catch (IOException | RuntimeException e) {
            return List.of("(" + d.id() + ".codes.yaml cannot be read: " + e.getMessage() + ")");
        }
    }

    /** Oracles: every case says where its expected values come from; every measure was recalculated in a workbook. */
    public static List<Result> oracles(Descriptor d, List<Cases.Case> cases, ExplainCheck.Counts explain) {
        List<String> p = new ArrayList<>();
        cases.stream().filter(c -> c.source() == null || c.source().isBlank()).forEach(c -> p.add(c.name() + " has no source"));
        boolean measures = d.outputs().values().stream().anyMatch(o -> !o.measures().isEmpty());
        if (measures && explain.names() == 0) {
            p.add("an output has measures, but no workbook was recalculated (no case answers a measure)");
        }
        if (cases.isEmpty()) {
            p.add("no cases");
        }
        return List.of(Result.of("gates", d.id(), "oracles", p.isEmpty() ? null : String.join("; ", p), null));
    }

    /** Additive only: every case of the previous release (same major), unchanged, is still there. */
    public static List<Result> additive(Descriptor d, List<Cases.Case> now, Path previous) throws IOException {
        if (previous == null) {
            return List.of(Result.ok("gates", d.id(), "additive only", "no previous release given"));
        }
        Map<String, String> current = new TreeMap<>();
        now.forEach(c -> current.put(c.name(), fingerprint(c)));
        List<String> p = new ArrayList<>();
        for (Cases.Case c : Cases.read(previous, d.id())) {
            String f = current.get(c.name());
            if (f == null) {
                p.add(c.name() + " was removed");
            } else if (!f.equals(fingerprint(c))) {
                p.add(c.name() + " was changed (a changed expected value is a new major)");
            }
        }
        return List.of(Result.of("gates", d.id(), "additive only", p.isEmpty() ? null : String.join("; ", p), null));
    }

    private static String fingerprint(Cases.Case c) {
        return Canonical.sha384(Canonical.json(Map.of("given", List.of(c.given().snapshot(), c.given().options(), c.given().rows()),
                "request", List.of(c.request().outputs(), c.request().params(), String.valueOf(c.request().visibility()), c.request().seed()),
                "expect", c.expect(), "changes", c.changes())));
    }
}
