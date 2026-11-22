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

import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/** What a host gives an engine instance at {@link Engine#open}: the only clock, threads, folder, budget and suppliers it may use. */
public interface EngineContext {

    /** The engine's options: typed getters by declared name. */
    Options options();

    /** Absolute, this instance's own, empty at open; the only place it may write. */
    Path workDir();

    /** Business time as the host defines it; the only clock an engine may read. */
    Clock clock();

    /** Bounded to the engine's thread limit; the only threads an engine may use. */
    ExecutorService executor();

    MemoryBudget memory();

    /** {@code audit.note(requestId, words)}: beside the host's own line per request. */
    Audit audit();

    /** debug, info, warn; never row values at info or above. */
    Log log();

    /** A {@code uses} port; empty when the site bound none. */
    Optional<Supplier> supplier(String port);

    /**
     * This engine at another snapshot, asked under the request's own visibility ({@link Supplier#ask}'s {@code onBehalfOf}): for an
     * output that compares two snapshots. The host finds or opens that session within the same limits. Empty: no rows there.
     */
    Optional<Supplier> peer(Snapshot other);

    /**
     * The stamp of an answer to {@code request} from this instance's session of {@code snapshot}: engine, version, descriptor and options
     * hashes, the input stamps the host delivered, the request's hash, the seed. A host builds it, so every engine's stamps have one
     * form; an engine calls it through {@link Stamps#of}.
     */
    Stamp stamp(Snapshot snapshot, Request request);
}
