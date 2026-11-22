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

/**
 * One step of an explanation. {@code formula}: Rachana-EL over {@code given} and earlier steps by name ({@code step.<name>}); null when
 * opaque. {@code given}: named values (numbers, text, lists, lists of records). {@code opaque}: the step is not expressible in EL; then
 * {@code why} says so and {@code cell} names the workbook cell that holds it. Build one with {@link Steps}.
 */
public record Step(String name, String label, String formula, SortedMap<String, Object> given, Object value,
        boolean opaque, String why, String cell) {

    public Step {
        given = Collections.unmodifiableSortedMap(new TreeMap<>(given == null ? new TreeMap<>() : given));
    }
}
