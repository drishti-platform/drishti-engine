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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TablesTest {

    static final TableSchema S = new TableSchema("t", List.of(ColumnSchema.of("K", "string", false), ColumnSchema.of("N", "integer", false),
            ColumnSchema.of("X", "number", true)), List.of("K"), List.of());

    @Test
    void rowsAreSortedByTheOrderAndKeysChecked() {
        Table t = Tables.of(S).row("b", 2L, 1.5).row("a", 1L, null).build();
        assertThat(t.texts("K")).containsExactly("a", "b");
        assertThat(t.isNull("X", 0)).isTrue();
        assertThat(t.numbers("X")[1]).isEqualTo(1.5);
        assertThatThrownBy(() -> Tables.of(S).row("a", 1L, 1.0).row("a", 2L, 2.0).build()).hasMessageContaining("not unique");
        assertThatThrownBy(() -> t.integers("K")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Tables.of(S).row("a", null, 1.0).build()).hasMessageContaining("not nullable");
        assertThatThrownBy(() -> Tables.of(S).row("a", 1L, Double.NaN).build()).hasMessageContaining("NaN");
    }

    @Test
    void wholeColumnsAreTakenNotCopied() {
        long[] n = {3, 4};
        Table t = Tables.of(S).columns(Map.of("K", new String[] {"x", "y"}, "N", n, "X", new double[] {1, Double.NaN})).build();
        assertThat(t.integers("N")).isSameAs(n);
        assertThat(t.isNull("X", 1)).isTrue();
    }

    @Test
    void aStampIsOneStableLine() {
        Stamp s = new Stamp("e", "1.0.0", "d", "o", new java.util.TreeMap<>(Map.of("in", "s1")), "r", 7);
        assertThat(s.text()).isEqualTo("e@1.0.0 d=d o=o i:in=s1 r=r s=7");
    }
}
