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
package com.ash.engine;

/**
 * The states of an engine instance (specification 7.1). The host owns the state; an engine does not track it for the host, but
 * the tck proves that it behaves as the table says.
 */
public enum LifecycleState {
    /** Constructed; nothing called. */
    NEW,
    /** {@code init} returned: descriptor and options read and validated, no heavy resource held. */
    INITIALISED,
    /** {@code start} returned (or {@code resume}): requests and sessions are served. */
    RUNNING,
    /** {@code suspend} returned: no new requests or sessions; state kept; {@code resume} is cheap. */
    SUSPENDED,
    /** {@code shutdown} returned: everything released; final. */
    SHUTDOWN,
    /** {@code init} refused or threw; only {@code shutdown} follows. */
    FAILED;

    /** True when requests and new sessions are accepted. */
    public boolean serving() {
        return this == RUNNING;
    }
}
