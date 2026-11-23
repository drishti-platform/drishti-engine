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

import com.ash.engine.Control;
import com.ash.engine.Engine;
import com.ash.engine.EngineContext;
import com.ash.engine.EngineException;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.EngineStats;
import com.ash.engine.Health;
import com.ash.engine.Inputs;
import com.ash.engine.LifecycleState;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One engine instance and its lifecycle (specification 7.1): init, start, suspend, resume, shutdown. Every call into the engine runs
 * with the engine's loader as the thread's context loader. A call in a state that does not allow it is {@code ENG-5020}; a request or
 * a session on a suspended instance is {@code ENG-5023} ("suspended").
 */
public final class EngineInstance implements AutoCloseable {

    private final Engine engine;
    private final ClassLoader loader;
    private final String id;
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile LifecycleState state = LifecycleState.NEW;
    private volatile String failure;
    private volatile String reason;

    public EngineInstance(String id, Engine engine, ClassLoader loader) {
        this.id = id;
        this.engine = engine;
        this.loader = loader;
    }

    public LifecycleState state() {
        return state;
    }

    /** Why init failed, in the engine's words (null when it did not). */
    public String failure() {
        return failure;
    }

    /** Why the instance is suspended (the administrator's or the licence's words), null when it is not. */
    public String suspendedBecause() {
        return state == LifecycleState.SUSPENDED ? reason : null;
    }

    public Engine engine() {
        return engine;
    }

    /** Requests running now (calls made through {@link #request}). */
    public int inFlight() {
        return inFlight.get();
    }

    /** NEW to INITIALISED; a refusal leaves it FAILED with the engine's words. */
    public synchronized void init(EngineContext context) throws EngineException {
        require(LifecycleState.NEW, "init");
        try {
            within(() -> {
                engine.init(context);
                return null;
            });
            state = LifecycleState.INITIALISED;
        } catch (EngineException e) {
            state = LifecycleState.FAILED;
            failure = e.getMessage();
            throw e;
        }
    }

    /** INITIALISED to RUNNING; a refusal leaves it FAILED (only shutdown follows). */
    public synchronized void start() throws EngineException {
        require(LifecycleState.INITIALISED, "start");
        try {
            within(() -> {
                engine.start();
                return null;
            });
            state = LifecycleState.RUNNING;
        } catch (EngineException e) {
            state = LifecycleState.FAILED;
            failure = e.getMessage();
            throw e;
        }
    }

    /** init then start, for a host that has nothing to do between them. */
    public void boot(EngineContext context) throws EngineException {
        init(context);
        start();
    }

    /**
     * RUNNING to SUSPENDED. New requests and sessions are refused from the first moment; requests in flight get {@code grace} (the
     * descriptor's cancel-latency) to finish, then {@code cancelInFlight} runs (the host cancels them through their Control) and they
     * get {@code grace} again; then the engine's {@code suspend} is called. State and resources stay.
     */
    public void suspend(String why, Duration grace, Runnable cancelInFlight) throws EngineException {
        synchronized (this) {
            require(LifecycleState.RUNNING, "suspend");
            reason = why == null || why.isBlank() ? "suspended by the administrator" : why;
            state = LifecycleState.SUSPENDED;                              // the gate: request() and session() refuse from here
        }
        drain(grace);
        if (inFlight.get() > 0 && cancelInFlight != null) {
            cancelInFlight.run();
            drain(grace);
        }
        synchronized (this) {
            if (state != LifecycleState.SUSPENDED) {
                return;                                                    // shut down meanwhile
            }
            try {
                within(() -> {
                    engine.suspend();
                    return null;
                });
            } catch (EngineException e) {
                state = LifecycleState.FAILED;
                failure = e.getMessage();
                throw e;
            }
        }
    }

    /** SUSPENDED to RUNNING; cheap. */
    public synchronized void resume() throws EngineException {
        require(LifecycleState.SUSPENDED, "resume");
        try {
            within(() -> {
                engine.resume();
                return null;
            });
            reason = null;
            state = LifecycleState.RUNNING;
        } catch (EngineException e) {
            state = LifecycleState.FAILED;
            failure = e.getMessage();
            throw e;
        }
    }

    /**
     * The instance's health (specification 7.4): the engine's own verdict, corrected by the host where it contradicts the lifecycle
     * state the host owns (the host's words are added, the engine's kept). An engine that throws is DEGRADED while running. Cheap; calls
     * no I/O of its own; safe from any thread in any state.
     */
    public Health health() {
        LifecycleState s = state;
        Instant now = Instant.now();
        Health own;
        try {
            own = engine.health();
        } catch (RuntimeException | LinkageError e) {
            own = null;
        }
        if (own == null) {
            Health base = Health.of(s, now);
            return s == LifecycleState.RUNNING ? Health.degraded(now, "the engine's health check failed") : withState(base, s);
        }
        if (Health.consistent(own.state(), s)) {
            return own;
        }
        List<String> why = new ArrayList<>(own.reasons());
        why.add("the engine reported " + own.state() + " while the instance is " + s);
        Health.State want = s == LifecycleState.RUNNING ? Health.State.DEGRADED
                : s == LifecycleState.SUSPENDED ? Health.State.SUSPENDED : Health.State.DOWN;
        return new Health(want, why, own.checkedAt());
    }

    private Health withState(Health base, LifecycleState s) {
        if (s == LifecycleState.SUSPENDED && reason != null) {
            return Health.suspended(base.checkedAt(), reason);
        }
        if (s == LifecycleState.FAILED && failure != null) {
            return Health.down(base.checkedAt(), failure);
        }
        return base;
    }

    /** The engine's counters (specification 7.5); empty when the engine's {@code stats()} throws. */
    public EngineStats stats() {
        try {
            EngineStats own = engine.stats();
            return own == null ? EngineStats.empty(Instant.now()) : own;
        } catch (RuntimeException | LinkageError e) {
            return EngineStats.empty(Instant.now());
        }
    }

    /** A session of the running instance. */
    public Session session(Snapshot snapshot, Inputs inputs, Control control) throws EngineException {
        admit();
        return within(() -> engine.session(snapshot, inputs, control));
    }

    /** Throws {@code ENG-5023} when suspended, {@code ENG-5020} when not running at all; returns when requests are served. */
    public void admit() throws EngineException {
        LifecycleState s = state;
        if (s == LifecycleState.RUNNING) {
            return;
        }
        if (s == LifecycleState.SUSPENDED) {
            throw new EngineException("ENG-5023", Kind.REFUSED, id + " is suspended" + (reason == null ? "" : ": " + reason));
        }
        throw new EngineException("ENG-5020", Kind.REFUSED, id + " is " + s + "; requests and sessions need a running instance");
    }

    /** A request on a session: refused unless RUNNING, counted while it runs (suspend waits for it). */
    public <T> T request(Callable<T> call) throws EngineException {
        inFlight.incrementAndGet();
        try {
            admit();
            return within(call);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    /** Runs {@code call} with the engine's loader as the context loader; an EngineException passes, anything else is {@code ENG-9000}. */
    public <T> T within(Callable<T> call) throws EngineException {
        Thread t = Thread.currentThread();
        ClassLoader before = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            return call.call();
        } catch (EngineException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new EngineException("ENG-9000", Kind.FAILED, id + " failed: " + e, Map.of(), e);
        } finally {
            t.setContextClassLoader(before);
        }
    }

    /** Any state to SHUTDOWN (sessions are the caller's to close first); harmless twice. */
    public synchronized void shutdown() {
        if (state == LifecycleState.SHUTDOWN) {
            return;
        }
        state = LifecycleState.SHUTDOWN;
        reason = null;
        try {
            within(() -> {
                engine.shutdown();
                return null;
            });
        } catch (EngineException e) {
            failure = "shutdown failed";
        }
    }

    /** Same as {@link #shutdown}. */
    @Override
    public void close() {
        shutdown();
    }

    private void require(LifecycleState expected, String call) throws EngineException {
        if (state != expected) {
            throw new EngineException("ENG-5020", Kind.REFUSED, id + " is " + state + "; " + call + " needs " + expected);
        }
    }

    private void drain(Duration grace) {
        long end = System.nanoTime() + Math.max(grace == null ? 0 : grace.toNanos(), 1_000_000L);
        while (inFlight.get() > 0 && System.nanoTime() < end) {
            Thread.onSpinWait();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
