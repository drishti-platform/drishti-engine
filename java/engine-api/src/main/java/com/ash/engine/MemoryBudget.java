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

/** The host's memory budget: reserve before allocating more than 1 MB; release at close or when the request ends. */
public interface MemoryBudget {

    /** Reserves before allocating. Throws EngineException ENG-5002 (in words) when the budget cannot give it. */
    Reservation reserve(long bytes, String what) throws EngineException;

    /** Bytes held under one reservation. */
    interface Reservation extends AutoCloseable {

        void resize(long bytes) throws EngineException;

        @Override
        void close();
    }
}
