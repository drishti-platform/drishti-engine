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
import java.util.Optional;
import java.util.Set;

/** Capability {@code incremental}. Implemented by the Session. */
public interface Incremental {

    /**
     * Applies the changes atomically with respect to ask: a request sees the state before or after, never between. Called by one thread
     * at a time. false: the engine cannot apply these; the host reopens the session.
     */
    boolean apply(List<Changes> changes, Control control) throws EngineException;

    /** The keys of {@code output} an applied change may have touched, or empty for "cannot say" (the host re-asks all subscribed). */
    default Optional<Set<String>> affected(String output, List<Changes> changes) {
        return Optional.empty();
    }
}
