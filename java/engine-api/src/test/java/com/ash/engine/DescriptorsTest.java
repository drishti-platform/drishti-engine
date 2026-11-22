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

import org.junit.jupiter.api.Test;

class DescriptorsTest {

    static final String MINIMAL = """
            engine: 1
            id: calc
            version: 1.0.0
            title: A calculator
            about: Adds two numbers.
            publisher: { id: org.example, name: Example }
            api: "0.1"
            inputs: {}
            params:
              a: { type: number, default: 0, label: A }
              b: { type: number, default: 0, label: B }
            outputs:
              sum:
                title: Sum
                about: a plus b
                shape: row
                params: [ a, b ]
                columns:
                  - { name: SUM, type: number, nullable: false, label: Sum, measure: true }
            capabilities: [ explain, workbook ]
            """;

    @Test
    void readsAMinimalDescriptorWithNoInputs() {
        Descriptor d = Descriptors.parse(MINIMAL);
        assertThat(d.id()).isEqualTo("calc");
        assertThat(d.inputs()).isEmpty();
        assertThat(d.schema("sum").columns().get(0).exact()).isEqualTo("bits");
        assertThat(Descriptors.problems(d)).isEmpty();
        assertThat(Descriptors.parse(MINIMAL)).isEqualTo(d);
    }

    @Test
    void unknownKeysAndTheDeletedEntryAreRefusedWithThePlace() {
        assertThatThrownBy(() -> Descriptors.parse(MINIMAL + "colour: red\n")).hasMessageStartingWith("ENG-1001").hasMessageContaining("colour");
        assertThatThrownBy(() -> Descriptors.parse(MINIMAL + "entry: org.example.Calc\n")).hasMessageContaining("deleted");
        assertThatThrownBy(() -> Descriptors.parse(MINIMAL.replace("api: \"0.1\"", "api: 0.1"))).hasMessageContaining("api");
        assertThatThrownBy(() -> Descriptors.parse(MINIMAL.replace("id: calc", "id: Calc"))).hasMessageContaining("id");
    }

    @Test
    void theRulesTheSchemaCannotSayAreWords() {
        Descriptor d = Descriptors.parse(MINIMAL.replace("capabilities: [ explain, workbook ]", "capabilities: [ explain ]"));
        assertThat(Descriptors.problems(d)).anyMatch(p -> p.contains("explain and workbook"));
        Descriptor e = Descriptors.parse(MINIMAL.replace("b: { type: number, default: 0, label: B }", "b: { type: number, default: 0 }"));
        assertThat(Descriptors.problems(e)).anyMatch(p -> p.contains("params.b has no label"));
    }
}
