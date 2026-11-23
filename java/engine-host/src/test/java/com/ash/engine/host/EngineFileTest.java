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
import org.junit.jupiter.api.Test;

class EngineFileTest {

    static final Descriptor D = Descriptors.parse(EngineJarTest.DESCRIPTOR.replace("inputs: {}", """
            inputs:
              rows: { contract: x/row@1, about: Rows., columns: [ A ], changes: rows }
            capabilities: [ incremental ]
            options:
              scale: { type: number, default: 1, label: Scale }"""));

    static final String FILE = """
            drishti:
              engines:
                calc:
                  enabled: ${DRISHTI_CALC_ENABLED:true}
                  changed: { by: "", at: "", reason: "" }
                  options: { scale: 2 }
                  triggers:
                    - { on: request }
                    - { on: changes, input: rows, from: live:rows }
                    - { on: schedule, cron: "0 30 18 * * MON-FRI", zone: America/Toronto, ask: nightly }
                  asks:
                    nightly: { outputs: [ sum ], as: system }
                  outputs:
                    sum: { serve: [ protocol ], live: true }
                  access: { opens: [ role:analyst ], visibility: core.all-rows }
                  limits: { request-seconds: 5, concurrent: { interactive: 4 } }
            """;

    @Test
    void readsTheWholeFileAndHoldsItAgainstTheDescriptor() throws Exception {
        EngineFile f = EngineFile.read("calc", FILE);
        assertThat(f.enabled()).isTrue();
        assertThat(f.triggers()).extracting(EngineFile.Trigger::on).containsExactly("request", "changes", "schedule");
        assertThat(f.limits().concurrent()).containsEntry("interactive", 4);
        assertThat(f.check(D)).isEmpty();
    }

    @Test
    void anUnknownKeyOrADeletedKeyIsRefusedWithTheKey() {
        assertThatThrownBy(() -> EngineFile.read("calc", FILE.replace("changed:", "colour: red\n      changed:")))
                .isInstanceOfSatisfying(EngineException.class, e -> assertThat(e.code()).isEqualTo("ENG-1010")).hasMessageContaining("colour");
        assertThatThrownBy(() -> EngineFile.read("calc", FILE.replace("options: { scale: 2 }", "engine: { artifact: x.jar }")))
                .hasMessageContaining("deleted");
    }

    @Test
    void namesTheDescriptorDoesNotHaveAreProblems() throws Exception {
        EngineFile f = EngineFile.read("calc", FILE.replace("options: { scale: 2 }", "options: { size: 2 }").replace("sum: { serve", "total: { serve"));
        assertThat(f.check(D)).extracting(Problem::code).contains("ENG-1010", "ENG-1013");
    }

    @Test
    void theTemplateReadsBackAsAnEngineFile() throws Exception {
        String t = EngineTemplate.of(D).replace("<a host rule>", "core.all-rows");
        EngineFile f = EngineFile.read("calc", t);
        assertThat(f.check(D)).isEmpty();
    }

    @Test
    void anInputIsReadOnOrBeforeTheDateOrFromAListFieldWhenTheFileSaysSo() throws Exception {
        EngineFile f = EngineFile.read("calc", FILE.replace("options: { scale: 2 }",
                "options: { scale: 2 }\n      inputs:\n        rows: { kinds: [ limit ], as-of: on-or-before, field: BUCKETS }"));
        EngineFile.Binding b = f.inputs().get("rows");
        assertThat(b.onOrBefore()).isTrue();
        assertThat(b.field()).isEqualTo("BUCKETS");
        assertThat(EngineFile.read("calc", FILE.replace("options: { scale: 2 }",
                "options: { scale: 2 }\n      inputs:\n        rows: { kinds: [ limit ] }")).inputs().get("rows").asOf()).isEqualTo("date");
        assertThatThrownBy(() -> EngineFile.read("calc", FILE.replace("options: { scale: 2 }",
                "options: { scale: 2 }\n      inputs:\n        rows: { kinds: [ limit ], as-of: latest }"))).hasMessageContaining("on-or-before");
    }
}
