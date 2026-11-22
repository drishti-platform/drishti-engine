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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * An engine lib folder read at start (section 17.4): every {@code .jar} directly in it, in name order, opened with {@link EngineJar};
 * each engine id put in exactly one state with its words. No state of any engine stops the host or another engine. This is the
 * reference discovery every host uses; a host adds only where its engine files come from.
 */
public final class EngineLib implements AutoCloseable {

    /** An engine's state, as a host shows it. */
    public enum State {
        RUNNING("running"), OFF("off"), NOT_CONFIGURED("not configured"), NOT_INSTALLED("not installed"), BLOCKED("blocked");

        private final String words;

        State(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** One engine id: its state, why, and (when installed) the registration and the JAR. */
    public record Entry(String id, State state, String words, String code, EngineJar.Registered engine, EngineJar jar) {}

    /** One JAR of the folder as a host lists it: file, SHA-384, engines and versions, publishers, certificate, notes. */
    public record JarLine(String file, String sha384, List<String> engines, List<String> publishers, boolean certificate, List<String> notes) {}

    private final Path folder;
    private final List<EngineJar> jars;
    private final Map<String, Entry> entries;
    private final List<JarLine> lines;

    private EngineLib(Path folder, List<EngineJar> jars, Map<String, Entry> entries, List<JarLine> lines) {
        this.folder = folder;
        this.jars = jars;
        this.entries = entries;
        this.lines = lines;
    }

    /**
     * Reads {@code folder} (absent or empty: no engines). {@code engineFiles}: the engine ids that have an engine file, each with its
     * {@code enabled} switch. {@code host}: the loader of the platform classes.
     */
    public static EngineLib scan(Path folder, ClassLoader host, Provided provided, Map<String, Boolean> engineFiles) throws IOException {
        return scan(folder, host, provided, engineFiles, Licence.ALL);
    }

    /** As {@link #scan(Path, ClassLoader, Provided, Map)}, with the installation's licence: an engine whose product it does not grant is blocked ({@code ENG-1014}). */
    public static EngineLib scan(Path folder, ClassLoader host, Provided provided, Map<String, Boolean> engineFiles, Licence licence) throws IOException {
        List<EngineJar> jars = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> s = Files.list(folder)) {
                for (Path p : s.filter(x -> x.getFileName().toString().endsWith(".jar") && Files.isRegularFile(x)).sorted().toList()) {
                    jars.add(EngineJar.open(p, host, provided));
                }
            }
        }
        Map<String, List<EngineJar>> owners = new TreeMap<>();
        for (EngineJar j : jars) {
            for (EngineJar.Registered r : j.engines()) {
                if (!"?".equals(r.id())) {
                    owners.computeIfAbsent(r.id(), k -> new ArrayList<>()).add(j);
                }
            }
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, List<EngineJar>> o : owners.entrySet()) {
            String id = o.getKey();
            if (o.getValue().size() > 1) {
                String files = String.join(" and ", o.getValue().stream().map(j -> j.path().getFileName().toString()).toList());
                entries.put(id, new Entry(id, State.BLOCKED, "two JARs register " + id + ": " + files + ". An upgrade replaces the file: keep one.",
                        "ENG-1006", null, null));
                continue;
            }
            EngineJar j = o.getValue().get(0);
            EngineJar.Registered r = j.engines().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
            java.util.Optional<Problem> unlicensed = r.ok() ? licence.check(r.descriptor(), j.path().getFileName().toString()) : java.util.Optional.empty();
            if (unlicensed.isPresent()) {
                entries.put(id, new Entry(id, State.BLOCKED, unlicensed.get().words(), unlicensed.get().code(), r, j));
            } else if (!r.ok()) {
                entries.put(id, new Entry(id, State.BLOCKED, r.problems().get(0).words(), r.problems().get(0).code(), r, j));
            } else if (!engineFiles.containsKey(id)) {
                entries.put(id, new Entry(id, State.NOT_CONFIGURED, id + " " + r.version() + " is installed (" + j.path().getFileName()
                        + ") but has no engine file. A template is made from its descriptor.", "ENG-5034", r, j));
            } else if (Boolean.TRUE.equals(engineFiles.get(id))) {
                entries.put(id, new Entry(id, State.RUNNING, id + " " + r.version() + " from " + j.path().getFileName(), null, r, j));
            } else {
                entries.put(id, new Entry(id, State.OFF, id + " " + r.version() + " is switched off", "ENG-5031", r, j));
            }
        }
        for (String id : new TreeSet<>(engineFiles.keySet())) {
            if (!entries.containsKey(id)) {
                entries.put(id, new Entry(id, State.NOT_INSTALLED, "the engine file names " + id + ", which no JAR in " + folder + " provides.",
                        "ENG-5030", null, null));
            }
        }
        List<JarLine> lines = new ArrayList<>();
        for (EngineJar j : jars) {
            List<String> notes = new ArrayList<>();
            j.problems().forEach(p -> notes.add(p.toString()));
            if (!j.bundledPlatformClasses().isEmpty()) {
                notes.add("bundles " + j.bundledPlatformClasses().size() + " platform class(es), never used: " + j.bundledPlatformClasses().get(0));
            }
            String file = j.path().getFileName().toString();
            lines.add(new JarLine(file, j.sha384(), j.engines().stream().map(r -> r.id() + " " + r.version()).toList(),
                    j.engines().stream().map(EngineJar.Registered::publisher).distinct().toList(),
                    Files.isRegularFile(j.path().resolveSibling(file + ".certificate.json")), List.copyOf(notes)));
        }
        return new EngineLib(folder, List.copyOf(jars), entries, List.copyOf(lines));
    }

    public Path folder() {
        return folder;
    }

    /** Every engine id, in name order of discovery, with its state. */
    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /** The entry of {@code id}, or null when neither a JAR nor an engine file names it. */
    public Entry entry(String id) {
        return entries.get(id);
    }

    /** Every JAR of the folder as a host lists it. */
    public List<JarLine> jars() {
        return lines;
    }

    @Override
    public void close() {
        jars.forEach(EngineJar::close);
    }
}
