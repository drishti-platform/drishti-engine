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

import java.util.SortedMap;

/** Another engine's output (a {@code uses} port), or this engine at another snapshot: asked with the current request's visibility. */
public interface Supplier {

    Answer ask(String output, SortedMap<String, Object> params, Request onBehalfOf, Control control) throws EngineException;
}
