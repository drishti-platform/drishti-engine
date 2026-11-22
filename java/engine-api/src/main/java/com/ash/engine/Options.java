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

/** The engine instance's options, valid against the descriptor, defaults filled. */
public interface Options {

    /** Every option by name, defaults filled; values are String, Long, Double, Boolean or a list of them. */
    SortedMap<String, Object> all();

    /** One option's value; IllegalArgumentException for a name the descriptor does not declare. */
    default Object value(String name) {
        if (!all().containsKey(name)) {
            throw new IllegalArgumentException("not a declared option: " + name);
        }
        return all().get(name);
    }

    default String text(String name) {
        Object v = value(name);
        return v == null ? null : v.toString();
    }

    default long integer(String name) {
        return ((Number) value(name)).longValue();
    }

    default double number(String name) {
        return ((Number) value(name)).doubleValue();
    }

    default boolean flag(String name) {
        return Boolean.TRUE.equals(value(name));
    }
}
