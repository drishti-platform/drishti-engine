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
import com.ash.engine.EngineStats;
import com.ash.engine.Health;
import com.ash.engine.LifecycleState;
import com.ash.engine.Request;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import com.ash.engine.host.EngineInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The observability laws (specification 7.4 and 7.5), on the engine itself (not the host's corrected view): health agrees with the
 * lifecycle state in every state; health and stats never block (bounded time, also while a request runs on another thread); stats
 * counters are monotonic, agree with the requests the kit made, are untouched by requests the host refused, and the gauges return to
 * zero.
 */
final class Observability {

    private static final String GROUP = "observability";
    /** The bound for one health() or stats() call, in milliseconds. */
    private static final long BOUND_MS = 50;
    private static final int ASKS = 5;

    private Observability() {
    }

    static List<Result> law(KitHost host, Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        String s = c.name();
        Descriptor d = host.descriptor();
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            Engine engine = host.within(() -> host.subject().create());
            EngineInstance inst = new EngineInstance(d.id(), engine, Thread.currentThread().getContextClassLoader());
            List<String> bad = new ArrayList<>();
            agree(bad, engine, LifecycleState.NEW);
            inst.init(o.context());
            agree(bad, engine, LifecycleState.INITIALISED);
            inst.start();
            agree(bad, engine, LifecycleState.RUNNING);
            EngineStats zero = engine.stats();
            Session session = inst.session(Snapshot.of(c.given().snapshot()), Checks.inputsOf(host, c), o.control());
            Request req = KitHost.request(d, c, KitHost.Variation.PLAIN);
            List<String> mono = new ArrayList<>();
            EngineStats previous = zero;
            AtomicBoolean done = new AtomicBoolean();
            AtomicReference<String> slow = new AtomicReference<>();
            Thread poller = new Thread(() -> {
                while (!done.get()) {
                    String x = timed(engine);
                    if (x != null) {
                        slow.compareAndSet(null, x);
                    }
                }
            }, "kit-health-poller");
            poller.start();
            for (int i = 0; i < ASKS; i++) {
                inst.request(() -> session.ask(req, o.control()));
                EngineStats now = engine.stats();
                String m = monotonic(previous, now);
                if (m != null) {
                    mono.add(m);
                }
                previous = now;
            }
            done.set(true);
            poller.join(2_000);
            out.add(Result.of(GROUP, s, "health bounded time", slow.get() != null ? slow.get() : timed(engine), null));
            EngineStats after = engine.stats();
            EngineStats.Counters t = after.sinceStart();
            EngineStats.Counters z = zero.sinceStart();
            List<String> count = new ArrayList<>();
            if (t.asked() - z.asked() != ASKS) {
                count.add("the kit asked " + ASKS + " requests and asked rose by " + (t.asked() - z.asked()));
            }
            if (t.finished() - z.finished() != ASKS) {
                count.add("finished rose by " + (t.finished() - z.finished()) + ", expected " + ASKS);
            }
            if (t.answered() - z.answered() != ASKS) {
                count.add("answered rose by " + (t.answered() - z.answered()) + ", expected " + ASKS + " (refused " + t.refused()
                        + ", failed " + t.failed() + ", cancelled " + t.cancelled() + ")");
            }
            if (t.asked() < t.finished()) {
                count.add("asked " + t.asked() + " is below finished " + t.finished());
            }
            if (!(t.p50Nanos() <= t.p95Nanos() && t.p95Nanos() <= t.p99Nanos())) {
                count.add("latency percentiles are not ordered: p50 " + t.p50Nanos() + ", p95 " + t.p95Nanos() + ", p99 " + t.p99Nanos());
            }
            if (t.p99Nanos() <= 0) {
                count.add("latency was not recorded");
            }
            if (after.sessionsOpen() != 1) {
                count.add("sessionsOpen is " + after.sessionsOpen() + " with one session open");
            }
            if (after.lastActivity() == null) {
                count.add("lastActivity is empty after requests");
            }
            if (after.recent().asked() > t.asked()) {
                count.add("the recent window asked " + after.recent().asked() + " exceeds since-start " + t.asked());
            }
            out.add(Result.of(GROUP, s, "stats monotonic", mono.isEmpty() ? null : String.join("; ", mono), null));
            out.add(Result.of(GROUP, s, "stats match the requests made", count.isEmpty() ? null : String.join("; ", count), null));
            inst.suspend("the kit suspends the engine", d.limits().cancelLatency(), null);
            agree(bad, engine, LifecycleState.SUSPENDED);
            try {
                inst.request(() -> session.ask(req, o.control()));
                bad.add("a request on a suspended engine was answered");
            } catch (EngineException e) {
                // the host refused: the engine was not asked, so its counters stay
            }
            EngineStats suspended = engine.stats();
            String m = monotonic(after, suspended);
            String unchanged = suspended.sinceStart().asked() == t.asked() ? null
                    : "a request the host refused while suspended changed asked from " + t.asked() + " to " + suspended.sinceStart().asked();
            out.add(Result.of(GROUP, s, "stats keep across suspend", m != null ? m : unchanged, null));
            inst.resume();
            agree(bad, engine, LifecycleState.RUNNING);
            inst.within(() -> {
                session.close();
                return null;
            });
            String gauge = engine.stats().sessionsOpen() == 0 ? null : "sessionsOpen is " + engine.stats().sessionsOpen() + " after close";
            out.add(Result.of(GROUP, s, "sessions gauge returns to zero", gauge, null));
            inst.shutdown();
            agree(bad, engine, LifecycleState.SHUTDOWN);
            out.add(Result.of(GROUP, s, "health matches lifecycle", bad.isEmpty() ? null : String.join("; ", bad), null));
        } catch (Exception e) {
            out.add(Result.fail(GROUP, s, "observability", e.toString()));
        }
        return out;
    }

    /** The engine's own health in {@code lifecycle} is a verdict that may accompany it, with words when not plain UP. */
    private static void agree(List<String> bad, Engine engine, LifecycleState lifecycle) {
        try {
            Health h = engine.health();
            if (h == null) {
                bad.add("health() returned null in " + lifecycle);
            } else if (!Health.consistent(h.state(), lifecycle)) {
                bad.add("health() is " + h.state() + " while the instance is " + lifecycle);
            } else if (h.state() != Health.State.UP && h.reasons().isEmpty()) {
                bad.add("health() is " + h.state() + " in " + lifecycle + " without a reason in words");
            }
        } catch (RuntimeException e) {
            bad.add("health() threw in " + lifecycle + ": " + e);
        }
    }

    /** One health() and one stats() call within the bound (null: yes). */
    private static String timed(Engine engine) {
        long worst = 0;
        for (int i = 0; i < 200; i++) {
            long t0 = System.nanoTime();
            engine.health();
            engine.stats();
            worst = Math.max(worst, System.nanoTime() - t0);
        }
        long ms = worst / 1_000_000;
        return ms <= BOUND_MS ? null : "health() and stats() took " + ms + " ms (bound " + BOUND_MS + " ms)";
    }

    /** Every cumulative counter of {@code since-start} is at least what it was (null: yes). */
    private static String monotonic(EngineStats a, EngineStats b) {
        EngineStats.Counters x = a.sinceStart();
        EngineStats.Counters y = b.sinceStart();
        long[][] pairs = {{x.asked(), y.asked()}, {x.answered(), y.answered()}, {x.refused(), y.refused()}, {x.failed(), y.failed()},
            {x.cancelled(), y.cancelled()}, {x.rowsIn(), y.rowsIn()}, {x.rowsOut(), y.rowsOut()}, {x.bytesIn(), y.bytesIn()},
            {x.bytesOut(), y.bytesOut()}, {x.cacheHits(), y.cacheHits()}, {x.cacheMisses(), y.cacheMisses()}};
        String[] names = {"asked", "answered", "refused", "failed", "cancelled", "rowsIn", "rowsOut", "bytesIn", "bytesOut", "cacheHits",
            "cacheMisses"};
        for (int i = 0; i < pairs.length; i++) {
            if (pairs[i][1] < pairs[i][0]) {
                return names[i] + " went backwards from " + pairs[i][0] + " to " + pairs[i][1];
            }
        }
        return null;
    }
}
