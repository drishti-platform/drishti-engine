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

import java.util.Map;
import java.util.Set;

/**
 * What a request may be computed from. {@code signature}: equal for two visibilities exactly when they are the same rule (an engine keys
 * any per-viewer cache by it). {@code rows}: input name to filter, for every input that declares scope columns. {@code masked}: columns
 * of outputs the host will mask (an engine MAY skip computing them).
 */
public record Visibility(String signature, Map<String, Filter> rows, Set<String> masked) {

    public Visibility {
        rows = Map.copyOf(rows == null ? Map.of() : rows);
        masked = Set.copyOf(masked == null ? Set.of() : masked);
    }
}
