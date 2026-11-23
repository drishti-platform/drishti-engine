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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The zero-copy views (specification 18.3): row sets, the heap and float views, and the sum order (19.4: rows ascending). */
class ViewsTest {

    @Test
    void aRowSetIsAscendingRunsAndCombines() {
        RowSet a = RowSet.of(5, 1, 2, 3, 9);
        List<String> runs = new ArrayList<>();
        a.forEachRun((f, t) -> runs.add(f + ".." + t));
        assertThat(runs).containsExactly("1..4", "5..6", "9..10");
        assertThat(a.size()).isEqualTo(5);
        assertThat(a.and(RowSet.range(2, 6)).toArray()).containsExactly(2, 3, 5);
        assertThat(a.andNot(RowSet.of(9)).or(RowSet.of(0)).toArray()).containsExactly(0, 1, 2, 3, 5);
        assertThat(RowSet.all(3)).isEqualTo(RowSet.of(2, 0, 1));
    }

    @Test
    void aFloatViewSumsWidenedInRowOrderAndCannotBeWritten() {
        float[] v = {0.1f, 1f, 0.2f, 2f, 0.3f, 3f};
        FloatVectors f = VectorViews.floats(FloatBuffer.wrap(v), 2, Lease.FOREVER);
        assertThat(f.rows()).isEqualTo(3);
        double[] acc = new double[2];
        f.addInto(acc, RowSet.of(0, 2));
        double[] want = new double[2];
        want[0] = (double) 0.1f;
        want[0] += (double) 0.3f;
        want[1] = 1d + 3d;
        assertThat(Double.doubleToRawLongBits(acc[0])).isEqualTo(Double.doubleToRawLongBits(want[0]));
        assertThat(acc[1]).isEqualTo(want[1]);
        assertThat(f.row(1).isReadOnly()).isTrue();
        assertThat(f.value(1, 1)).isEqualTo(2f);
    }

    @Test
    void aHeapViewWrapsTheArraysWithoutCopying() {
        double[] r0 = {1, 2};
        DoubleVectors d = VectorViews.heap(List.of(r0, new double[] {3, 4}), 2);
        r0[0] = 10;                                    // the view reads the array itself
        assertThat(d.value(0, 0)).isEqualTo(10);
        double[] acc = new double[2];
        d.addInto(acc, RowSet.all(2));
        assertThat(acc).containsExactly(13, 6);
        assertThat(d.row(0).isReadOnly()).isTrue();
    }
}
