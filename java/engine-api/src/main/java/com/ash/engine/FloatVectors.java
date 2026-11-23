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

import java.nio.FloatBuffer;

/**
 * Fixed-width float rows, row-major, read-only (specification 18.3): a position's scenario P&L, a node's aggregate. Row {@code i} is the
 * input's row {@code i} in scan order. Backed by the host's memory (a heap chunk or a mapped file), never copied for the engine.
 */
public interface FloatVectors {

    /** The rows. */
    int rows();

    /** Every row's length. */
    int width();

    /** The lease the rows are read under. */
    Lease lease();

    /** Row {@code i}, a read-only slice of {@link #width()} values. */
    FloatBuffer row(int i);

    /** Value {@code j} of row {@code i}. */
    float value(int i, int j);

    /**
     * Adds the rows of {@code rows} into {@code acc} (length at least {@link #width()}), each value widened to double, the rows in ascending
     * ordinal order: the reduction order of specification 19.4 inside one block.
     */
    default void addInto(double[] acc, RowSet rows) {
        int w = width();
        rows.forEachRun((from, to) -> {
            for (int i = from; i < to; i++) {
                FloatBuffer r = row(i);
                for (int j = 0; j < w; j++) {
                    acc[j] += r.get(j);
                }
            }
        });
    }
}
