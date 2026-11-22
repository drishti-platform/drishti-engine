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

/** The engine's loaded state for one snapshot: answers any number of requests, concurrently. */
public interface Session extends AutoCloseable {

    Snapshot snapshot();

    /** Bytes held now: heap, direct and mapped. Cheap; called often. */
    long memoryBytes();

    /** Thread-safe: called for many requests at once. Never returns null. */
    Answer ask(Request request, Control control) throws EngineException;

    /** Idempotent. After it, memoryBytes() is 0 and no thread of the host's executor still runs this session's work. */
    @Override
    void close();
}
