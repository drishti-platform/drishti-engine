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
import com.ash.engine.EngineException;
import com.ash.engine.LifecycleState;
import com.ash.engine.Request;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import com.ash.engine.host.EngineInstance;
import com.ash.engine.host.HostControl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The lifecycle law (specification 7.1): every legal transition, every illegal one refused as {@code ENG-5020}, suspend then resume keeps
 * answers bit-identical and is cheap, a suspended instance refuses with {@code ENG-5023} in the word "suspended", shutdown is idempotent
 * and leaves no thread and no file of the engine.
 */
final class Lifecycle {

    private static final String GROUP = "lifecycle";

    private Lifecycle() {
    }

    /** The law on one case. */
    static List<Result> law(KitHost host, Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        String s = c.name();
        Descriptor d = host.descriptor();
        Set<Thread> before = new HashSet<>(Thread.getAllStackTraces().keySet());
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            Engine engine = host.within(() -> host.subject().create());
            EngineInstance inst = new EngineInstance(d.id(), engine, Thread.currentThread().getContextClassLoader());
            out.add(Result.of(GROUP, s, "legal transitions", transitions(inst, o), null));
            // a second instance, running, for the suspend and resume law
            Engine second = host.within(() -> host.subject().create());
            EngineInstance run = new EngineInstance(d.id(), second, Thread.currentThread().getContextClassLoader());
            run.boot(o.context());
            Duration latency = d.limits().cancelLatency();
            Session session = run.session(Snapshot.of(c.given().snapshot()), Checks.inputsOf(host, c), o.control());
            Request req = KitHost.request(d, c, KitHost.Variation.PLAIN);
            Outcome one = Outcome.of(run.request(() -> session.ask(req, o.control())));
            long held = session.memoryBytes();
            long t0 = System.nanoTime();
            run.suspend("the kit suspends the engine", latency, null);
            long suspendMs = (System.nanoTime() - t0) / 1_000_000;
            out.add(Result.of(GROUP, s, "suspended refuses", suspendedRefuses(run, session, req, c, host, o), null));
            String kept = session.memoryBytes() == held ? null : "memoryBytes() was " + held + " before suspend and " + session.memoryBytes() + " during it";
            long t1 = System.nanoTime();
            run.resume();
            long resumeMs = (System.nanoTime() - t1) / 1_000_000;
            Outcome two = Outcome.of(run.request(() -> session.ask(req, o.control())));
            String diff = Compare.same(one, two);
            out.add(Result.of(GROUP, s, "suspend and resume keep the answer", kept != null ? kept : diff, one.hash()));
            long bound = Math.max(latency.toMillis(), 1) * 2 + 50;
            out.add(Result.of(GROUP, s, "suspend and resume are cheap", suspendMs <= bound && resumeMs <= bound ? null
                    : "suspend took " + suspendMs + " ms and resume " + resumeMs + " ms (bound " + bound + " ms)", null));
            run.within(() -> {
                session.close();
                return null;
            });
            run.shutdown();
            run.shutdown();
            out.add(Result.of(GROUP, s, "shutdown is idempotent", run.state() == LifecycleState.SHUTDOWN ? null
                    : "the state after shutdown is " + run.state(), null));
            engine.shutdown();
            String leftover = files(o.workDir());
            out.add(Result.of(GROUP, s, "no file left after shutdown", leftover, null));
        } catch (Exception e) {
            out.add(Result.fail(GROUP, s, "lifecycle states", e.toString()));
        }
        out.add(Result.of(GROUP, s, "no thread left after shutdown", threads(before), null));
        return out;
    }

    /** NEW to SHUTDOWN, every legal step taken and every illegal call refused as ENG-5020. Null when it holds. */
    private static String transitions(EngineInstance i, KitHost.Opened o) {
        List<String> bad = new ArrayList<>();
        expect(bad, i, LifecycleState.NEW);
        refused(bad, "start on NEW", () -> i.start());
        refused(bad, "resume on NEW", () -> i.resume());
        refused(bad, "suspend on NEW", () -> i.suspend(null, Duration.ZERO, null));
        step(bad, "init", () -> i.init(o.context()));
        expect(bad, i, LifecycleState.INITIALISED);
        refused(bad, "init twice", () -> i.init(o.context()));
        refused(bad, "resume on INITIALISED", () -> i.resume());
        refused(bad, "suspend on INITIALISED", () -> i.suspend(null, Duration.ZERO, null));
        step(bad, "start", () -> i.start());
        expect(bad, i, LifecycleState.RUNNING);
        refused(bad, "start on RUNNING", () -> i.start());
        refused(bad, "resume on RUNNING", () -> i.resume());
        step(bad, "suspend", () -> i.suspend(null, Duration.ofMillis(50), null));
        expect(bad, i, LifecycleState.SUSPENDED);
        refused(bad, "suspend on SUSPENDED", () -> i.suspend(null, Duration.ZERO, null));
        refused(bad, "start on SUSPENDED", () -> i.start());
        step(bad, "resume", () -> i.resume());
        expect(bad, i, LifecycleState.RUNNING);
        i.shutdown();
        expect(bad, i, LifecycleState.SHUTDOWN);
        refused(bad, "start on SHUTDOWN", () -> i.start());
        refused(bad, "resume on SHUTDOWN", () -> i.resume());
        i.shutdown();
        expect(bad, i, LifecycleState.SHUTDOWN);
        return bad.isEmpty() ? null : String.join("; ", bad);
    }

    private interface Call {
        void run() throws EngineException;
    }

    private static void step(List<String> bad, String what, Call call) {
        try {
            call.run();
        } catch (EngineException | RuntimeException e) {
            bad.add(what + " was refused: " + e.getMessage());
        }
    }

    private static void refused(List<String> bad, String what, Call call) {
        try {
            call.run();
            bad.add(what + " was accepted");
        } catch (EngineException e) {
            if (!"ENG-5020".equals(e.code()) || e.kind() != EngineException.Kind.REFUSED) {
                bad.add(what + " gave " + e.code() + " " + e.kind() + ", not ENG-5020 REFUSED");
            }
        } catch (RuntimeException e) {
            bad.add(what + " threw " + e);
        }
    }

    private static void expect(List<String> bad, EngineInstance i, LifecycleState want) {
        if (i.state() != want) {
            bad.add("the state is " + i.state() + ", expected " + want);
        }
    }

    /** A request and a new session on a suspended instance are ENG-5023 REFUSED with the word "suspended". */
    private static String suspendedRefuses(EngineInstance run, Session session, Request req, Cases.Case c, KitHost host, KitHost.Opened o) {
        try {
            run.request(() -> session.ask(req, o.control()));
            return "a request on a suspended engine was answered";
        } catch (EngineException e) {
            if (!"ENG-5023".equals(e.code()) || e.kind() != EngineException.Kind.REFUSED || !e.getMessage().contains("suspended")) {
                return "a request on a suspended engine gave " + e.code() + " " + e.kind() + " \"" + e.getMessage() + "\", not ENG-5023 REFUSED with the word suspended";
            }
        }
        try {
            run.session(Snapshot.of(c.given().snapshot()), Checks.inputsOf(host, c), o.control());
            return "a session was opened on a suspended engine";
        } catch (EngineException e) {
            return "ENG-5023".equals(e.code()) ? null : "a session on a suspended engine gave " + e.code() + ", not ENG-5023";
        }
    }

    /** Files the engine left under its work folder after shutdown (null: none). */
    private static String files(Path work) throws java.io.IOException {
        if (work == null || !Files.exists(work)) {
            return null;
        }
        try (Stream<Path> w = Files.walk(work)) {
            List<Path> left = w.filter(Files::isRegularFile).toList();
            return left.isEmpty() ? null : left.size() + " file(s) remain under the work folder, first " + work.relativize(left.get(0));
        }
    }

    /** Threads alive that were not alive before the run, once the host's own executor is down (null: none). */
    private static String threads(Set<Thread> before) {
        long end = System.nanoTime() + 2_000_000_000L;
        Supplier<List<Thread>> extra = () -> Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && !before.contains(t) && t != Thread.currentThread()).toList();
        List<Thread> left = extra.get();
        while (!left.isEmpty() && System.nanoTime() < end) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            left = extra.get();
        }
        return left.isEmpty() ? null : left.size() + " thread(s) still run after shutdown, first " + left.get(0).getName();
    }
}
