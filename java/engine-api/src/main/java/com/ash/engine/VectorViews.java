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

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.List;

/** Views over memory handed over by value: a kit case's rows, a test's arrays, a host without views yet (specification 18.3). */
public final class VectorViews {

    private VectorViews() {
    }

    /** float64 rows on the heap, each wrapped read-only (no copy of the arrays); a shorter row reads as zeros past its end. */
    public static DoubleVectors heap(List<double[]> rows, int width) {
        double[][] r = rows.toArray(new double[0][]);
        return new DoubleVectors() {
            @Override
            public int rows() {
                return r.length;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public Lease lease() {
                return Lease.FOREVER;
            }

            @Override
            public DoubleBuffer row(int i) {
                double[] a = r[i].length == width ? r[i] : java.util.Arrays.copyOf(r[i], width);
                return DoubleBuffer.wrap(a).asReadOnlyBuffer();
            }

            @Override
            public double value(int i, int j) {
                return j < r[i].length ? r[i][j] : 0d;
            }

            @Override
            public void addInto(double[] acc, RowSet set) {
                set.forEachRun((from, to) -> {
                    for (int i = from; i < to; i++) {
                        double[] a = r[i];
                        for (int j = 0; j < a.length; j++) {
                            acc[j] += a[j];
                        }
                    }
                });
            }
        };
    }

    /** float32 rows in one read-only buffer, row-major, {@code width} values a row (a mapped file's slice, or a test's). */
    public static FloatVectors floats(FloatBuffer all, int width, Lease lease) {
        FloatBuffer b = all.asReadOnlyBuffer();
        int n = width == 0 ? 0 : b.limit() / width;
        return new FloatVectors() {
            @Override
            public int rows() {
                return n;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public Lease lease() {
                return lease;
            }

            @Override
            public FloatBuffer row(int i) {
                return b.slice(i * width, width);
            }

            @Override
            public float value(int i, int j) {
                return b.get(i * width + j);
            }
        };
    }
}
