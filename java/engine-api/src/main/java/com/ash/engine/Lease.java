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

/**
 * The lifetime of a set of views and the version they show (specification 18.2). A request lease covers one ask, a session lease the
 * session's snapshot version. The host opens and closes leases; a view read after its lease closed refuses ("unloaded, ask again").
 */
public interface Lease {

    /** A lease that never closes: views over memory the engine was handed by value (a kit case, a test). */
    Lease FOREVER = new Lease() {
        @Override
        public String version() {
            return "";
        }

        @Override
        public boolean open() {
            return true;
        }
    };

    /** The snapshot version the views show. */
    String version();

    /** False once the host has closed the lease. */
    boolean open();

    /** Throws the refusal a read after the lease's end gets. */
    default void check() throws EngineException {
        if (!open()) {
            throw new EngineException("ENG-5024", EngineException.Kind.REFUSED, "the data of version " + version() + " was unloaded while this "
                    + "answer was computed; ask again");
        }
    }
}
