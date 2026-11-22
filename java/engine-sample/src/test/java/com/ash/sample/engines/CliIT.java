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

import com.ash.engine.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The sample JAR run by the command line with no host: a folder of JSONL in, tables, the answer, an explanation and a workbook out. */
class CliIT {

    @Test
    void aDayOfFilesInTablesAndAWorkbookOut(@TempDir Path dir) throws Exception {
        Path in = Files.createDirectories(dir.resolve("in"));
        Files.writeString(in.resolve("observations.jsonl"), """
                {"ID":"o1","GROUP":"A","VALUE":10,"WEIGHT":1}
                {"ID":"o2","GROUP":"A","VALUE":20,"WEIGHT":3}
                {"ID":"o3","GROUP":"B","VALUE":5,"WEIGHT":2}
                """, StandardCharsets.UTF_8);
        Path out = dir.resolve("out");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int rc = Main.run(new String[] {"run", "--engine", System.getProperty("engine.jar"), "--id", "wmean", "--inputs", in.toString(),
            "--outputs", "mean-by-group", "--snapshot", "date=2026-01-13", "--out", out.toString(), "--workbook",
            "--explain", "mean-by-group:MEAN:GROUP=A"}, new PrintStream(o, true, StandardCharsets.UTF_8), new PrintStream(e, true, StandardCharsets.UTF_8));
        assertThat(rc).as(e.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(Files.readAllLines(out.resolve("mean-by-group.jsonl"))).containsExactly(
                "{\"COUNT\":2,\"GROUP\":\"A\",\"MEAN\":17.5,\"WEIGHT\":4}", "{\"COUNT\":1,\"GROUP\":\"B\",\"MEAN\":5,\"WEIGHT\":2}");
        assertThat(Files.readString(out.resolve("answer.json"))).contains("\"status\":\"COMPLETE\"").contains("wmean@1.0.0");
        assertThat(Files.size(out.resolve("wmean.xlsx"))).isPositive();
        assertThat(Files.readString(out.resolve("explain.json"))).contains("step.product / step.weight");
    }

    @Test
    void aMissingFileIsAnAnswerThatSaysWhat(@TempDir Path dir) throws Exception {
        Path in = Files.createDirectories(dir.resolve("in"));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int rc = Main.run(new String[] {"run", "--engine", System.getProperty("engine.jar"), "--id", "wmean", "--inputs", in.toString(),
            "--outputs", "mean-by-group", "--snapshot", "date=2026-01-13", "--out", dir.resolve("out").toString()},
                new PrintStream(o, true, StandardCharsets.UTF_8), System.err);
        assertThat(rc).isEqualTo(1);
        assertThat(o.toString(StandardCharsets.UTF_8)).contains("not found: stats/observation@1 for date 2026-01-13");
    }
}
