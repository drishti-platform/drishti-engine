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
     * Releases everything: threads, files, native handles, memory. Sessions are already closed. Callable in any state, also after a
     * failed {@link #init}, and idempotent: a second call does nothing and does not throw.
     */
    void shutdown();
}
