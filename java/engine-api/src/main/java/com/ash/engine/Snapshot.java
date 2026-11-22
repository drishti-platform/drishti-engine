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
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** The dimensions of the descriptor's snapshot block, each as canonical text. Empty when the descriptor declares none. */
public record Snapshot(SortedMap<String, String> dimensions) {

    /** The snapshot with no dimensions. */
    public static final Snapshot NONE = new Snapshot(new TreeMap<>());

    public Snapshot {
        dimensions = Collections.unmodifiableSortedMap(new TreeMap<>(dimensions == null ? Map.of() : dimensions));
    }

    /** A snapshot of the given dimensions (any map; kept sorted by name). */
    public static Snapshot of(Map<String, String> dimensions) {
        return new Snapshot(new TreeMap<>(dimensions));
    }

    /** One line, stable: {@code date=2026-01-13}, dimensions joined by {@code ,}; empty for none. */
    public String text() {
        StringBuilder b = new StringBuilder();
        dimensions.forEach((k, v) -> b.append(b.length() == 0 ? "" : ",").append(k).append('=').append(v));
        return b.toString();
    }
}
