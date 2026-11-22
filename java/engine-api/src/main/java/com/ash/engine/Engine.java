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
public interface Engine extends AutoCloseable {

    /** The descriptor, read from the jar's own file ({@link Descriptors#of}). MUST equal that file. */
    Descriptor descriptor();

    /** Called once. Options are already valid against the descriptor. Refuse with an EngineException (the reason is shown). */
    void open(EngineContext context) throws EngineException;

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

    /** Releases everything. Sessions are already closed. */
    @Override
    void close();
}
