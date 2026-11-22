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

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One request. {@code id}: the correlation id. {@code outputs}: the outputs asked, in order. {@code params}: normalised (defaults filled,
 * types converted, sorted by name). {@code actor}: for the audit line only; an engine never keeps it nor varies an answer by it.
 */
public record Request(String id, List<String> outputs, SortedMap<String, Object> params, Visibility visibility,
        String actor, long seed, RequestClass requestClass) {

    public Request {
        outputs = List.copyOf(outputs);
        params = Collections.unmodifiableSortedMap(new TreeMap<>(params == null ? new TreeMap<>() : params));
    }

    /** A parameter's value, or null when the output was asked without it and it has no default. */
    public Object param(String name) {
        return params.get(name);
    }
}
