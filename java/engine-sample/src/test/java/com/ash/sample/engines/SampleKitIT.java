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

import com.ash.engine.tck.KitRun;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The kit on the packaged JAR, as a host will meet it (acceptance tests A1 and A2): loaded through EngineJarLoader with only the
 * platform packages shared, every case, every law, the explanations and workbooks, the gates. No server is involved.
 */
class SampleKitIT {

    @Test
    void bothEnginesOfTheJarPassTheKit() throws Exception {
        Path jar = Path.of(System.getProperty("engine.jar"));
        List<KitRun.Report> reports = KitRun.jar(jar, new KitRun.Settings(Path.of(System.getProperty("engine.cases")),
                Path.of(System.getProperty("engine.kit.out")), null, null));
        assertThat(reports).extracting(KitRun.Report::engine).containsExactly("wmean", "group-totals");
        for (KitRun.Report r : reports) {
            assertThat(r.failures()).as(r.label()).isEmpty();
        }
        assertThat(reports.get(0).lawsPassed()).contains("repeat", "threads", "batching", "order", "filter-equivalence", "interleaving",
                "incremental", "additivity", "seed", "explain", "overlay", "pushdown", "interim", "isolation");
        assertThat(reports.get(1).lawsPassed()).contains("pushdown", "isolation", "interleaving");
    }
}
