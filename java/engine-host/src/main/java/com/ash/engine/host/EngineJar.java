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

import com.ash.engine.Assembling;
import com.ash.engine.Descriptor;
import com.ash.engine.Descriptors;
import com.ash.engine.Engine;
import com.ash.engine.text.Canonical;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * One engine JAR as a host meets it (sections 17.2, 17.3, 17.5): opened in its own {@link EngineJarLoader}, its engines found by
 * {@link ServiceLoader}, each registration matched to its descriptor file, each descriptor checked against what the host provides. The
 * findings are words with codes; nothing here throws for a bad JAR.
 */
public final class EngineJar implements AutoCloseable {

    /** The services file that registers engine classes. */
    public static final String SERVICES = "META-INF/services/com.ash.engine.Engine";

    /** One engine the JAR registers. {@code problems}: why it is blocked (empty: it may start). */
    public record Registered(String id, String version, String publisher, Class<? extends Engine> type, Descriptor descriptor,
            List<Problem> problems) {

        /** True when no check failed. */
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private final Path path;
    private final String sha384;
    private final EngineJarLoader loader;
    private final List<String> classes;
    private final List<String> bundled;
    private final List<String> services;
    private final Map<String, String> descriptorFiles;
    private final List<Registered> engines;
    private final List<Problem> problems;

    private EngineJar(Path path, String sha384, EngineJarLoader loader, List<String> classes, List<String> bundled, List<String> services,
            Map<String, String> descriptorFiles, List<Registered> engines, List<Problem> problems) {
        this.path = path;
        this.sha384 = sha384;
        this.loader = loader;
        this.classes = classes;
        this.bundled = bundled;
        this.services = services;
        this.descriptorFiles = descriptorFiles;
        this.engines = engines;
        this.problems = problems;
    }

    /**
     * Opens {@code jar} with platform classes from {@code host}, against what the host provides. A JAR that cannot be read gives an
     * EngineJar with no engines and an {@code ENG-1007} problem.
     */
    public static EngineJar open(Path jar, ClassLoader host, Provided provided) {
        String file = jar.getFileName().toString();
        List<String> classes = new ArrayList<>();
        List<String> bundled = new ArrayList<>();
        List<String> services = new ArrayList<>();
        Map<String, String> descriptors = new TreeMap<>();
        List<Problem> problems = new ArrayList<>();
        String sha;
        try {
            sha = Canonical.sha384(Files.readAllBytes(jar));
            try (JarFile jf = new JarFile(jar.toFile())) {
                Enumeration<JarEntry> en = jf.entries();
                while (en.hasMoreElements()) {
                    JarEntry e = en.nextElement();
                    String n = e.getName();
                    if (n.endsWith(".class") && !n.endsWith("module-info.class") && !n.startsWith("META-INF/")) {
                        String cn = n.substring(0, n.length() - 6).replace('/', '.');
                        classes.add(cn);
                        if (Platform.isPlatform(cn)) {
                            bundled.add(cn);
                        }
                    } else if (n.startsWith(Descriptors.FOLDER) && n.endsWith(".engine.yaml")) {
                        try (InputStream in = jf.getInputStream(e)) {
                            descriptors.put(n, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    } else if (n.equals(SERVICES)) {
                        try (InputStream in = jf.getInputStream(e)) {
                            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(l -> l.replaceAll("#.*", "").strip())
                                    .filter(l -> !l.isEmpty()).forEach(services::add);
                        }
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            problems.add(new Problem("ENG-1007", file + " is not readable as an engine JAR: " + e.getClass().getSimpleName()));
            return new EngineJar(jar, null, null, List.of(), List.of(), List.of(), Map.of(), List.of(), problems);
        }
        Collections.sort(classes);
        if (descriptors.isEmpty() && services.isEmpty()) {
            problems.add(new Problem("ENG-1007", file + " holds no engine: no " + SERVICES + " and no " + Descriptors.FOLDER + "<id>.engine.yaml"));
        }
        Map<String, Descriptor> byId = new LinkedHashMap<>();
        Map<String, Boolean> matched = new TreeMap<>();
        for (Map.Entry<String, String> d : descriptors.entrySet()) {
            try {
                Descriptor desc = Descriptors.parse(d.getValue());
                String expected = Descriptors.FOLDER + desc.id() + ".engine.yaml";
                if (!expected.equals(d.getKey())) {
                    problems.add(new Problem("ENG-1007", file + ": " + d.getKey() + " declares the id " + desc.id() + "; its file must be " + expected));
                }
                byId.put(desc.id(), desc);
                matched.put(desc.id(), desc.worker() != null);
            } catch (IllegalArgumentException e) {
                problems.add(new Problem("ENG-1001", file + ": " + d.getKey() + ": " + e.getMessage().replaceFirst("^ENG-1001 ", "")));
            }
        }
        EngineJarLoader loader = new EngineJarLoader(jar, host);
        List<Registered> engines = new ArrayList<>();
        Thread t = Thread.currentThread();
        ClassLoader before = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            for (ServiceLoader.Provider<Engine> p : ServiceLoader.load(Engine.class, loader).stream().toList()) {
                register(file, p, byId, matched, provided, engines);
            }
        } catch (ServiceConfigurationError e) {
            problems.add(new Problem("ENG-1003", file + ": a registered engine class cannot be loaded: " + rootWords(e)));
        } finally {
            t.setContextClassLoader(before);
        }
        matched.forEach((id, ok) -> {
            if (!ok) {
                problems.add(new Problem("ENG-1007", file + ": the descriptor " + id + " has no registered class in " + SERVICES));
            }
        });
        return new EngineJar(jar, sha, loader, List.copyOf(classes), List.copyOf(bundled), List.copyOf(services), descriptors,
                List.copyOf(engines), problems);
    }

    private static void register(String file, ServiceLoader.Provider<Engine> p, Map<String, Descriptor> byId, Map<String, Boolean> matched,
            Provided provided, List<Registered> engines) {
        Class<? extends Engine> type;
        try {
            type = p.type();
        } catch (ServiceConfigurationError e) {
            engines.add(new Registered("?", "?", "?", null, null, List.of(new Problem("ENG-1003", file + ": " + rootWords(e)))));
            return;
        }
        Descriptor said;
        try (Engine e = p.get()) {
            said = e.descriptor();
        } catch (RuntimeException | ServiceConfigurationError | LinkageError e) {
            engines.add(new Registered("?", "?", "?", type, null,
                    List.of(new Problem("ENG-1003", file + ": " + type.getSimpleName() + " cannot be created or does not give its descriptor: " + rootWords(e)))));
            return;
        }
        List<Problem> blocked = new ArrayList<>();
        Descriptor fileDesc = said == null ? null : byId.get(said.id());
        if (said == null || fileDesc == null) {
            blocked.add(new Problem("ENG-1003", file + ": " + type.getSimpleName() + " gives a descriptor that no " + Descriptors.FOLDER + "<id>.engine.yaml of the JAR holds"));
        } else {
            matched.put(said.id(), true);
            if (!said.equals(fileDesc)) {
                blocked.add(new Problem("ENG-1003", file + ": " + said.id() + ": descriptor() differs from " + Descriptors.FOLDER + said.id() + ".engine.yaml"));
            }
            blocked.addAll(compatibility(file, fileDesc, provided));
            boolean assembling = Assembling.class.isAssignableFrom(type);
            if (assembling != fileDesc.has("partition")) {
                blocked.add(new Problem("ENG-1004", file + ": " + said.id() + (assembling ? " implements Assembling without the capability partition"
                        : " lists the capability partition but its engine class does not implement Assembling")));
            }
        }
        Descriptor d = fileDesc != null ? fileDesc : said;
        engines.add(new Registered(d == null ? "?" : d.id(), d == null ? "?" : d.version(), d == null ? "?" : d.publisher().name(), type, d,
                List.copyOf(blocked)));
    }

    /** The API and platform checks of section 17.5, each failure one sentence naming both versions and what to do. */
    public static List<Problem> compatibility(String file, Descriptor d, Provided provided) {
        List<Problem> out = new ArrayList<>();
        if (!Versions.compatible(provided.api(), d.api())) {
            out.add(new Problem("ENG-1002", d.id() + " " + d.version() + " (" + file + ") was built for engine API " + d.api() + "; this host provides "
                    + Versions.majorMinor(provided.api()) + ". Install a host release that provides " + d.api()
                    + " or later, or the engine build for " + Versions.majorMinor(provided.api()) + ". Nothing else is affected."));
        }
        d.platform().forEach((lib, built) -> {
            String have = provided.of(lib);
            String name = switch (lib) {
                case "tools" -> "drishti-tools";
                case "lake" -> "Drishti Lake";
                default -> "Rachana";
            };
            if (have == null) {
                out.add(new Problem("ENG-1005", d.id() + " " + d.version() + " (" + file + ") needs " + name + " " + built
                        + "; this host provides none. Install a host release that provides it. Nothing else is affected."));
            } else if (!Versions.compatible(have, built)) {
                out.add(new Problem("ENG-1005", d.id() + " " + d.version() + " (" + file + ") needs " + name + " " + built + "; this host provides "
                        + Versions.majorMinor(have) + ". Install a host release that provides " + name + " " + built
                        + " or later, or the engine build for " + Versions.majorMinor(have) + ". Nothing else is affected."));
            }
        });
        return out;
    }

    private static String rootWords(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    public Path path() {
        return path;
    }

    /** SHA-384 of the file, lower-case hex; null when it could not be read. */
    public String sha384() {
        return sha384;
    }

    /** The JAR's loader (null when the JAR could not be read). */
    public EngineJarLoader loader() {
        return loader;
    }

    /** Every class in the JAR, dotted, sorted. */
    public List<String> classes() {
        return classes;
    }

    /** Classes of a platform package inside the JAR: never used; the kit refuses the JAR, a host notes it. */
    public List<String> bundledPlatformClasses() {
        return bundled;
    }

    /** The class names the services file registers. */
    public List<String> services() {
        return services;
    }

    /** The descriptor files, path to text. */
    public Map<String, String> descriptorFiles() {
        return descriptorFiles;
    }

    /** The engines it registers, with what blocks each. */
    public List<Registered> engines() {
        return engines;
    }

    /** Findings about the JAR as a whole. */
    public List<Problem> problems() {
        return problems;
    }

    /** Creates an engine of a registered class, with the JAR's loader as the thread's context loader for the constructor. */
    public Engine create(Registered r) throws ReflectiveOperationException {
        Thread t = Thread.currentThread();
        ClassLoader before = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            return r.type().getConstructor().newInstance();
        } finally {
            t.setContextClassLoader(before);
        }
    }

    @Override
    public void close() {
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException e) {
                // the file handle is the operating system's to drop; nothing is left to undo
            }
        }
    }
}
