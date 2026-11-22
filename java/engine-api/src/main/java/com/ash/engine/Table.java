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

/**
 * Immutable, columnar. An accessor for another type than the column's is an IllegalArgumentException. A host MAY read the arrays
 * without copying; an engine MUST NOT change them after the table is built. Build one with {@link Tables}.
 */
public interface Table {

    TableSchema schema();

    int rows();

    /** {@code string}, {@code decimal}, {@code date}, {@code timestamp}, {@code time}. */
    String[] texts(String column);

    long[] integers(String column);

    /** A null is NaN together with the null flag. */
    double[] numbers(String column);

    boolean[] flags(String column);

    double[][] lists(String column);

    boolean isNull(String column, int row);

    /** The cell as an object: String, Long, Double, Boolean, double[] or null. */
    default Object value(String column, int row) {
        if (isNull(column, row)) {
            return null;
        }
        return switch (schema().column(column).carrier()) {
            case TEXT -> texts(column)[row];
            case INTEGER -> integers(column)[row];
            case NUMBER -> numbers(column)[row];
            case FLAG -> flags(column)[row];
            case LIST -> lists(column)[row];
        };
    }
}
