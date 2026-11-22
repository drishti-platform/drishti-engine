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

/** {@code fn}: SUM, COUNT, MIN, MAX. {@code of}: Rachana-EL over a row (null for COUNT of rows). {@code where}: this aggregate's condition, or null. */
public record Aggregate(String name, Fn fn, String of, String where) {

    /** The closed list of push-down functions. */
    public enum Fn { SUM, COUNT, MIN, MAX }
}
