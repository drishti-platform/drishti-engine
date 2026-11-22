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

/** The one form of an answer's stamp. */
public final class Stamps {

    private Stamps() {
    }

    /** The stamp of an answer to {@code request} from {@code session}: built by the host ({@link EngineContext#stamp}). */
    public static Stamp of(EngineContext context, Session session, Request request) {
        return context.stamp(session.snapshot(), request);
    }

    /** The stamp of an answer to {@code request} for {@code snapshot}, for an engine that answers without a session ({@link Assembling}). */
    public static Stamp of(EngineContext context, Snapshot snapshot, Request request) {
        return context.stamp(snapshot, request);
    }
}
