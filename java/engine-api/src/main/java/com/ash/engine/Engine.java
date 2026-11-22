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
package com.ash.engine;

import java.util.List;

/**
 * An engine: one object per engine instance (per tenant, per test). Registered for {@link java.util.ServiceLoader} in its JAR; public,
 * with a public constructor without parameters that does no work. The engine starts no thread, keeps no timer, reads no clock but the
 * context's, and holds nothing derived from a request.
 */
public interface Engine {

    /** The descriptor, read from the jar's own file ({@link Descriptors#of}). MUST equal that file. */
    Descriptor descriptor();

    /**
     * First call, once. Reads the descriptor and the options in {@code context}, validates them, keeps the context. Acquires no thread,
     * file, native library or large memory (that is {@link #start}). Refuse with an EngineException (the reason is shown).
     */
    void init(EngineContext context) throws EngineException;

    /**
     * After {@link #init}: acquires resources (native libraries,
     * caches, work files) and warms what it will use. Sessions are opened only while the engine is running.
     */
    void start() throws EngineException;

    /**
     * Stops taking new work. The host has already stopped admitting requests and sessions and has let calls in flight finish within the
     * declared cancel-latency (or cancelled them through {@code Control}); the engine keeps all its state and resources. MUST be cheap:
     * no reload, no release of loaded data.
     */
    void suspend() throws EngineException;

    /** Back to running from {@link #suspend}; cheap; nothing is reloaded and no answer changes (the same bits as before the suspend). */
    void resume() throws EngineException;

    /**
     * Opens the loaded state for one snapshot. The engine reads what it keeps from {@code inputs} now or lazily at the first ask (memory
     * is reserved before it is taken). {@code inputs} stays valid until the session is closed. Absent required inputs do not fail the
     * open: they make answers NOT_FOUND.
     */
    Session session(Snapshot snapshot, Inputs inputs, Control control) throws EngineException;

    /** Status words for a host's administrator (a native library loaded, or fallen back to Java). Cheap; never row values. */
    default List<String> notes() {
        return List.of();
    }

    /**
     * The engine's own verdict (specification 7.4): {@code UP} or {@code DEGRADED} while running, {@code SUSPENDED} after {@link #suspend},
     * {@code DOWN} in every other state, so it always agrees with the lifecycle. Reasons in words. MUST be cheap and MUST NOT block or do
     * I/O: it reads what the engine already knows (flags, counters, the last result of a check it ran at {@link #start}) and is callable
     * in any state and from any thread, also while a request runs.
     */
    Health health();

    /**
     * A typed snapshot of the instance's counters (specification 7.5): requests asked, answered, refused, failed and cancelled; latency
     * p50, p95 and p99; rows and bytes in and out; cache hits and misses; sessions open; queue depth; last activity; since start and over
     * the recent window. Same cost rules as {@link #health}. Callable in any state; counters never go backwards while the instance lives.
     * {@link EngineMeter} records and snapshots all of it.
     */
    EngineStats stats();

    /**
     * Releases everything: threads, files, native handles, memory. Sessions are already closed. Callable in any state, also after a
     * failed {@link #init}, and idempotent: a second call does nothing and does not throw.
     */
    void shutdown();
}
