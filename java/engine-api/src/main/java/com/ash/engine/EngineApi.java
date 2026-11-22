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

/** The version of the engine API this artifact is: what a descriptor's {@code api} names when an engine is compiled against it. */
public final class EngineApi {

    /** {@code major.minor}. Below 1.0 the minor counts as the major (section 17.5). */
    public static final String VERSION = "0.1";

    /** The descriptor format this API reads ({@code engine: 1}). */
    public static final int FORMAT = 1;

    private EngineApi() {
    }
}
