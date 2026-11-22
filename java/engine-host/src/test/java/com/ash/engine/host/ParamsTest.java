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
package com.ash.engine.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.engine.Descriptor;
import com.ash.engine.Descriptors;
import com.ash.engine.EngineException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParamsTest {

    static final Descriptor D = Descriptors.parse("""
            engine: 1
            id: calc
            version: 1.0.0
            title: A calculator
            about: Adds.
            publisher: { id: org.example, name: Example }
            product: example-lab
            api: "0.1"
            inputs: {}
            params:
              a: { type: number, default: 0, min: 0, label: A }
              how: { type: enum, options: [ up, down ], default: up, label: How }
            outputs:
              sum: { title: Sum, about: a, shape: row, params: [ a, how ], columns: [ { name: SUM, type: number, label: Sum } ] }
            """);

    @Test
    void defaultsAreFilledAndTypesConverted() throws Exception {
        assertThat(Params.request(D, List.of("sum"), Map.of("a", 2L))).containsExactlyEntriesOf(new java.util.TreeMap<>(Map.of("a", 2.0, "how", "up")));
    }

    @Test
    void unknownOrOutsideIsRefusedWithTheCode() {
        assertThatThrownBy(() -> Params.request(D, List.of("sum"), Map.of("b", 1L))).isInstanceOfSatisfying(EngineException.class,
                e -> assertThat(e.code()).isEqualTo("ENG-3001"));
        assertThatThrownBy(() -> Params.request(D, List.of("sum"), Map.of("a", -1L))).isInstanceOfSatisfying(EngineException.class,
                e -> assertThat(e.code()).isEqualTo("ENG-3002"));
        assertThatThrownBy(() -> Params.request(D, List.of("sum"), Map.of("how", "sideways"))).isInstanceOfSatisfying(EngineException.class,
                e -> assertThat(e.code()).isEqualTo("ENG-3002"));
        assertThatThrownBy(() -> Params.request(D, List.of("difference"), Map.of())).isInstanceOfSatisfying(EngineException.class,
                e -> assertThat(e.code()).isEqualTo("ENG-3003"));
    }
}
