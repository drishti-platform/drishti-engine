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
package com.ash.sample.engines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.engine.Answer;
import com.ash.engine.Cell;
import com.ash.engine.EngineException;
import com.ash.engine.Explanation;
import com.ash.engine.Status;
import com.ash.engine.Table;
import com.ash.engine.tck.EngineHarness;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** An author's unit tests on the harness: no JAR, no case file, no host. */
class WmeanHarnessTest {

    private static final List<Map<String, Object>> ROWS = List.of(
            Map.of("ID", "o1", "GROUP", "A", "VALUE", 10.0, "WEIGHT", 1.0),
            Map.of("ID", "o2", "GROUP", "A", "VALUE", 20.0, "WEIGHT", 3.0),
            Map.of("ID", "o3", "GROUP", "B", "VALUE", 5.0, "WEIGHT", 2.0));

    @Test
    void theMeanByGroupIsTheWeightedMean() throws Exception {
        try (var s = EngineHarness.of(WmeanEngine.class).snapshot("date", "2026-01-13").rows("observations", ROWS).open()) {
            Answer a = s.ask(List.of("mean-by-group"), Map.of());
            assertThat(a.status()).isEqualTo(Status.COMPLETE);
            Table t = a.table("mean-by-group");
            assertThat(t.texts("GROUP")).containsExactly("A", "B");
            assertThat(t.numbers("MEAN")).containsExactly(17.5, 5.0);
            assertThat(a.stamp().engine()).isEqualTo("wmean");
        }
    }

    @Test
    void theExplanationEndsAtTheCell() throws Exception {
        try (var s = EngineHarness.of(WmeanEngine.class).snapshot("date", "2026-01-13").rows("observations", ROWS).open()) {
            Explanation e = s.explain(List.of("mean-by-group"), Map.of(), new Cell("mean-by-group", "MEAN", new TreeMap<>(Map.of("GROUP", "A"))));
            assertThat(e.steps()).hasSize(3);
            assertThat(e.steps().get(2).value()).isEqualTo(17.5);
            assertThat(s.workbook(List.of("mean-by-group"), Map.of())).isNotEmpty();
        }
    }

    @Test
    void anAbsentTableIsAnAnswerAndAWrongParameterARefusal() throws Exception {
        try (var s = EngineHarness.of(WmeanEngine.class).snapshot("date", "2026-01-13").open()) {
            assertThat(s.ask(List.of("mean-by-group"), Map.of()).status()).isEqualTo(Status.NOT_FOUND);
            assertThatThrownBy(() -> s.ask(List.of("mean-by-group"), Map.of("min-weight", -1)))
                    .isInstanceOfSatisfying(EngineException.class, e -> assertThat(e.code()).isEqualTo("ENG-3002"));
        }
    }

    @Test
    void aChangeIsAppliedWhole() throws Exception {
        try (var s = EngineHarness.of(WmeanEngine.class).snapshot("date", "2026-01-13").rows("observations", ROWS).open()) {
            s.apply(List.of(Map.of("input", "observations", "upserts", List.of(Map.of("ID", "o4", "GROUP", "A", "VALUE", 30.0, "WEIGHT", 4.0)))));
            assertThat(s.ask(List.of("mean-by-group"), Map.of()).table("mean-by-group").numbers("MEAN")[0]).isEqualTo(23.75);
        }
    }
}
