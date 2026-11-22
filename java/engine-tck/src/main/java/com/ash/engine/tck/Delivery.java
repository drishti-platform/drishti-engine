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
import com.ash.engine.EngineApi;
import com.ash.engine.host.EngineJar;
import com.ash.engine.host.Platform;
import com.ash.engine.host.Problem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The delivery group (section 6.1, specification 17.1 to 17.3 and 17.5 rule 1) and the static scan (8.4, 8.1 rule 3), on the JAR as it
 * will be copied to a host. The scan is a lint, not a sandbox.
 */
public final class Delivery {

    /** Class prefixes an engine never names: a host's (Drishti, Spring, servlets) and the kit's own host packages. */
    static final List<String> HOSTS = List.of("com/ash/drishti/", "org/springframework/", "jakarta/servlet/", "javax/servlet/",
            "com/ash/engine/host/", "com/ash/engine/tck/");

    /** Member references the static scan refuses, as {@code owner name} (name {@code *}: every member), with the rule's words. */
    static final Map<String, String> FORBIDDEN = new TreeMap<>(Map.ofEntries(
            Map.entry("java/net/Socket *", "opens a network connection"), Map.entry("java/net/ServerSocket *", "opens a network connection"),
            Map.entry("java/net/DatagramSocket *", "opens a network connection"), Map.entry("java/net/URL openConnection", "opens a network connection"),
            Map.entry("java/net/URL openStream", "opens a network connection"), Map.entry("java/net/http/HttpClient *", "opens a network connection"),
            Map.entry("java/nio/channels/SocketChannel *", "opens a network connection"),
            Map.entry("java/lang/ProcessBuilder *", "starts a process"), Map.entry("java/lang/Runtime exec", "starts a process"),
            Map.entry("java/lang/System exit", "exits the JVM"), Map.entry("java/lang/Runtime exit", "exits the JVM"), Map.entry("java/lang/Runtime halt", "exits the JVM"),
            Map.entry("java/lang/Thread <init>", "creates a thread"), Map.entry("java/lang/Thread start", "creates a thread"),
            Map.entry("java/lang/Thread ofPlatform", "creates a thread"), Map.entry("java/lang/Thread ofVirtual", "creates a thread"),
            Map.entry("java/lang/Thread startVirtualThread", "creates a thread"), Map.entry("java/util/concurrent/Executors *", "creates an executor"),
            Map.entry("java/util/concurrent/ThreadPoolExecutor <init>", "creates an executor"), Map.entry("java/util/concurrent/ForkJoinPool <init>", "creates an executor"),
            Map.entry("java/util/concurrent/ForkJoinPool commonPool", "uses threads that are not the host's"), Map.entry("java/util/Timer <init>", "creates a thread"),
            Map.entry("java/util/concurrent/CompletableFuture supplyAsync", "uses threads that are not the host's"),
            Map.entry("java/util/concurrent/CompletableFuture runAsync", "uses threads that are not the host's"),
            Map.entry("java/util/Collection parallelStream", "uses threads that are not the host's"),
            Map.entry("java/util/stream/Stream parallel", "uses threads that are not the host's"),
            Map.entry("java/lang/System currentTimeMillis", "reads a clock other than the context's"),
            Map.entry("java/lang/System nanoTime", "reads a clock other than the context's"), Map.entry("java/time/Instant now", "reads a clock other than the context's"),
            Map.entry("java/time/LocalDate now", "reads a clock other than the context's"), Map.entry("java/time/LocalDateTime now", "reads a clock other than the context's"),
            Map.entry("java/time/ZonedDateTime now", "reads a clock other than the context's"), Map.entry("java/time/Clock systemUTC", "reads a clock other than the context's"),
            Map.entry("java/time/Clock systemDefaultZone", "reads a clock other than the context's"),
            Map.entry("java/lang/System getenv", "reads the environment for behaviour"), Map.entry("java/lang/System getProperty", "reads a system property for behaviour"),
            Map.entry("java/lang/Boolean getBoolean", "reads a system property for behaviour"), Map.entry("java/lang/Integer getInteger", "reads a system property for behaviour"),
            Map.entry("java/lang/Long getLong", "reads a system property for behaviour"),
            Map.entry("java/lang/Math random", "draws random numbers not seeded by the request"),
            Map.entry("java/util/concurrent/ThreadLocalRandom current", "draws random numbers not seeded by the request"),
            Map.entry("java/lang/Class forName", "reaches classes by name (reflection)"),
            Map.entry("java/lang/reflect/AccessibleObject setAccessible", "reaches into other classes by reflection")));

    /** Concrete mutable types a static final field may not hold. */
    static final Set<String> MUTABLE = Set.of("Ljava/util/ArrayList;", "Ljava/util/HashMap;", "Ljava/util/HashSet;", "Ljava/util/LinkedList;",
            "Ljava/util/TreeMap;", "Ljava/util/TreeSet;", "Ljava/util/LinkedHashMap;", "Ljava/util/LinkedHashSet;",
            "Ljava/util/concurrent/ConcurrentHashMap;", "Ljava/lang/StringBuilder;", "Ljava/util/Random;");

    private Delivery() {
    }

    /** The delivery and scan lines of one JAR, for the engine {@code d} the run certifies. */
    public static List<Result> check(EngineJar jar, Descriptor d, ClassLoader host) throws IOException {
        List<Result> out = new ArrayList<>();
        String file = jar.path().getFileName().toString();
        List<Problem> jarProblems = new ArrayList<>(jar.problems());
        jar.engines().forEach(r -> jarProblems.addAll(r.problems()));
        out.add(Result.of("delivery", file, "loads", jarProblems.isEmpty() ? null : String.join("; ", jarProblems.stream().map(Problem::toString).toList()), null));
        out.add(Result.of("delivery", file, "registrations", jar.engines().size() == jar.services().size() ? null
                : jar.services().size() + " registered classes and " + jar.engines().size() + " engines found", null));
        out.add(Result.of("delivery", file, "no platform class", jar.bundledPlatformClasses().isEmpty() ? null
                : jar.bundledPlatformClasses().size() + " platform classes bundled, the first " + jar.bundledPlatformClasses().get(0), null));
        Map<String, ClassFiles.Info> infos = new TreeMap<>();
        try (JarFile jf = new JarFile(jar.path().toFile())) {
            for (JarEntry e : java.util.Collections.list(jf.entries())) {
                if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/") && !e.getName().endsWith("module-info.class")) {
                    try (InputStream in = jf.getInputStream(e)) {
                        ClassFiles.Info info = ClassFiles.read(in.readAllBytes());
                        infos.put(info.name(), info);
                    }
                }
            }
            String manifestApi = jf.getManifest() == null ? null : jf.getManifest().getMainAttributes().getValue("Engine-Api");
            out.add(Result.of("delivery", file, "built against", builtAgainst(d, manifestApi, infos), null));
        }
        out.add(Result.of("delivery", file, "links closed", links(infos, host), null));
        out.add(Result.of("delivery", file, "names no host", hosts(infos), null));
        out.addAll(scan(file, infos));
        return out;
    }

    private static String builtAgainst(Descriptor d, String manifestApi, Map<String, ClassFiles.Info> infos) {
        List<String> p = new ArrayList<>();
        if (manifestApi != null && !manifestApi.equals(d.api())) {
            p.add("the manifest says Engine-Api " + manifestApi + ", the descriptor " + d.api());
        }
        if (!com.ash.engine.host.Versions.compatible(EngineApi.VERSION, d.api())) {
            p.add("built for engine API " + d.api() + "; this kit is for " + EngineApi.VERSION);
        }
        Set<String> used = new TreeSet<>();
        infos.values().forEach(i -> i.classes().forEach(c -> {
            String lib = Platform.library(c.replace('/', '.'));
            if (lib != null && !"api".equals(lib)) {
                used.add(lib);
            }
        }));
        for (String lib : used) {
            if (!d.platform().containsKey(lib)) {
                p.add("the JAR references " + lib + " but the descriptor's platform does not state the version it was built against");
            }
        }
        for (String lib : d.platform().keySet()) {
            if (!used.contains(lib)) {
                p.add("the descriptor's platform states " + lib + ", which no class references");
            }
        }
        return p.isEmpty() ? null : String.join("; ", p);
    }

    private static String links(Map<String, ClassFiles.Info> infos, ClassLoader host) {
        Map<String, String> unresolved = new TreeMap<>();
        Map<String, Boolean> cache = new HashMap<>();
        for (ClassFiles.Info i : infos.values()) {
            for (String c : i.classes()) {
                if (infos.containsKey(c) || c.length() == 1) {
                    continue;
                }
                boolean ok = cache.computeIfAbsent(c, k -> resolves(k.replace('/', '.'), host));
                if (!ok) {
                    unresolved.putIfAbsent(c, i.name());
                }
            }
        }
        if (unresolved.isEmpty()) {
            return null;
        }
        Map.Entry<String, String> first = unresolved.entrySet().iterator().next();
        return unresolved.size() + " class reference(s) resolve nowhere (not in the JAR, the platform packages or the JDK); the first: "
                + first.getKey().replace('/', '.') + " from " + first.getValue().replace('/', '.');
    }

    private static boolean resolves(String dotted, ClassLoader host) {
        try {
            if (Platform.isPlatform(dotted)) {
                if (!Platform.isShared(dotted)) {
                    return false;
                }
                Class.forName(dotted, false, host);
            } else {
                Class.forName(dotted, false, ClassLoader.getPlatformClassLoader());
            }
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static String hosts(Map<String, ClassFiles.Info> infos) {
        for (ClassFiles.Info i : infos.values()) {
            for (String c : i.classes()) {
                for (String h : HOSTS) {
                    if (c.startsWith(h)) {
                        return i.name().replace('/', '.') + " names the host type " + c.replace('/', '.');
                    }
                }
            }
        }
        return null;
    }

    /** The static scan of every class in the JAR: forbidden calls and mutable static state. */
    static List<Result> scan(String file, Map<String, ClassFiles.Info> infos) {
        List<String> findings = new ArrayList<>();
        for (ClassFiles.Info i : infos.values()) {
            String cls = i.name().replace('/', '.');
            for (ClassFiles.Ref r : i.refs()) {
                String why = FORBIDDEN.getOrDefault(r.owner() + " " + r.name(), FORBIDDEN.get(r.owner() + " *"));
                if (why != null && !(r.name().equals("<init>") && "java/util/Random".equals(r.owner()))) {
                    findings.add(cls + " " + why + " (" + r.owner().replace('/', '.') + "." + r.name() + ")");
                }
                if ("java/util/Random".equals(r.owner()) && "<init>".equals(r.name()) && "()V".equals(r.descriptor())) {
                    findings.add(cls + " draws random numbers not seeded by the request (new Random())");
                }
            }
            for (ClassFiles.Field f : i.fields()) {
                if (!f.isStatic() || f.isSynthetic()) {
                    continue;
                }
                if (!f.isFinal()) {
                    findings.add(cls + "." + f.name() + " is mutable static state (static, not final)");
                } else if (f.descriptor().startsWith("[") || MUTABLE.contains(f.descriptor())) {
                    findings.add(cls + "." + f.name() + " is mutable static state (a static final " + f.descriptor() + ")");
                }
            }
        }
        List<Result> out = new ArrayList<>();
        out.add(Result.of("scan", file, "closed", findings.isEmpty() ? null : findings.size() + " finding(s): " + String.join("; ", findings), null));
        return out;
    }

    /** The scan of classes found on a class path (the harness, before a JAR exists): {@code path} is a classes folder. */
    public static List<Result> scanFolder(Path classes) throws IOException {
        Map<String, ClassFiles.Info> infos = new TreeMap<>();
        try (var s = java.nio.file.Files.walk(classes)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".class")).toList()) {
                ClassFiles.Info info = ClassFiles.read(java.nio.file.Files.readAllBytes(p));
                infos.put(info.name(), info);
            }
        }
        return scan(classes.getFileName().toString(), infos);
    }
}
