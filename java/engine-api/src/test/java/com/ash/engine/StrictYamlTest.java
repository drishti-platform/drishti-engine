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

import com.ash.engine.text.StrictYaml;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrictYamlTest {

    @Test
    void readsBlockAndFlowCollectionsAndScalars() {
        Map<String, Object> m = StrictYaml.readMap("""
                # a comment
                a: 1
                b: 1.5
                c: "0x4031800000000000"   # quoted text stays text
                d: [ x, 2, { e: true, f: null } ]
                g:
                  - h: one
                    i: two
                  - plain
                j: |
                  line one
                  line two
                k: 2026-01-13
                """);
        assertThat(m.get("a")).isEqualTo(1L);
        assertThat(m.get("b")).isEqualTo(1.5);
        assertThat(m.get("c")).isEqualTo("0x4031800000000000");
        assertThat(((List<?>) m.get("d")).subList(0, 2)).containsExactly("x", 2L);
        assertThat(((List<?>) m.get("d")).get(2)).isInstanceOf(Map.class);
        assertThat((List<?>) m.get("g")).hasSize(2);
        assertThat(((Map<?, ?>) ((List<?>) m.get("g")).get(0)).get("i")).isEqualTo("two");
        assertThat(m.get("j")).isEqualTo("line one\nline two\n");
        assertThat(m.get("k")).isEqualTo("2026-01-13");
        assertThat(m.keySet()).containsExactly("a", "b", "c", "d", "g", "j", "k");
    }

    @Test
    void refusesWhatItDoesNotRead() {
        assertThatThrownBy(() -> StrictYaml.read("a: 1\na: 2\n")).hasMessageContaining("repeated");
        assertThatThrownBy(() -> StrictYaml.read("a: !tag 1\n")).hasMessageContaining("tags");
        assertThatThrownBy(() -> StrictYaml.read("a: &x 1\n")).hasMessageContaining("anchors");
        assertThatThrownBy(() -> StrictYaml.read("a: { b: 1, b: 2 }\n")).hasMessageContaining("repeated");
        assertThatThrownBy(() -> StrictYaml.read("a: 1\n---\nb: 2\n")).hasMessageContaining("second document");
    }

    @Test
    void aSequenceMayStandAtTheIndentOfItsKey() {
        Map<String, Object> m = StrictYaml.readMap("cases:\n- id: one\n  x: 1\n- id: two\n");
        assertThat((List<?>) m.get("cases")).hasSize(2);
    }
}
