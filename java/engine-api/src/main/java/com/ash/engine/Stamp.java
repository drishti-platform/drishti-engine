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
import java.util.SortedMap;
import java.util.TreeMap;

/** The identity of an answer: engine, version, descriptor, options, input stamps, request, seed. The cache key and the provenance. */
public record Stamp(String engine, String version, String descriptor, String options, SortedMap<String, String> inputs,
        String request, long seed) {

    public Stamp {
        inputs = Collections.unmodifiableSortedMap(new TreeMap<>(inputs == null ? new TreeMap<>() : inputs));
    }

    /** One line, stable: the cache key and the provenance value. */
    public String text() {
        StringBuilder b = new StringBuilder(engine).append('@').append(version).append(" d=").append(descriptor).append(" o=").append(options);
        inputs.forEach((k, v) -> b.append(" i:").append(k).append('=').append(v));
        return b.append(" r=").append(request).append(" s=").append(seed).toString();
    }
}
