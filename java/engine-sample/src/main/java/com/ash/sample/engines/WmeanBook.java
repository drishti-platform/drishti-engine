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

import com.ash.engine.Filter;
import com.ash.engine.Table;
import com.ash.tools.xlsx.Sheet;
import com.ash.tools.xlsx.Workbook;
import java.io.OutputStream;

/**
 * The wmean workbook: {@code Data} holds the observations the request may see (values only, ID order); {@code Main} recomputes each
 * group's count, weight and mean with formulas over {@code Data}, the minimum weight in one cell, and names the first group's mean
 * {@code OUT_MEAN_BY_GROUP_MEAN}. Written with the family's one .xlsx writer (drishti-tools), the same bytes for the same request.
 */
final class WmeanBook {

    private WmeanBook() {
    }

    static void write(OutputStream out, Observations rows, Filter see, double min, Table answer) {
        try (Workbook wb = Workbook.to(out)) {
            Sheet data = wb.sheet("Data");
            data.row(1).text(0, "ID", "header").text(1, "GROUP", "header").text(2, "VALUE", "header").text(3, "WEIGHT", "header");
            int n = 0;
            java.util.List<Double> products = new java.util.ArrayList<>();
            if (rows != null) {
                for (int i = 0; i < rows.size(); i++) {
                    String g = rows.group[i];
                    if (!see.test(c -> "GROUP".equals(c) ? g : null)) {
                        continue;
                    }
                    data.next().text(0, rows.id[i], "normal").text(1, g, "normal").number(2, rows.value[i], "decimal").number(3, rows.weight[i], "decimal");
                    products.add(rows.value[i] * rows.weight[i]);
                    n++;
                }
            }
            int last = Math.max(2, n + 1);
            String grp = "Data!$B$2:$B$" + last;
            String wgt = "Data!$D$2:$D$" + last;
            // VALUE x WEIGHT per observation, one formula per Data row: the conditional sums below stay plain SUMIFS (no array
            // arithmetic, which Apache POI and older spreadsheet programs evaluate by implicit intersection)
            Sheet work = wb.sheet("Work");
            work.row(1).text(0, "VALUE x WEIGHT", "header").text(1, "ONE", "header");
            for (int k = 2; k <= n + 1; k++) {
                work.row(k).formula(0, "Data!C" + k + "*Data!D" + k, products.get(k - 2), "decimal").number(1, 1, "int");
            }
            String vw = "Work!$A$2:$A$" + last;
            String one = "Work!$B$2:$B$" + last;
            Sheet main = wb.sheet("Main");
            main.row(1).text(0, "Minimum weight", "bold").number(1, min, "decimal");
            main.row(3).text(0, "GROUP", "header").text(1, "COUNT", "header").text(2, "WEIGHT", "header").text(3, "MEAN", "header");
            int r = 3;
            for (int i = 0; answer != null && i < answer.rows(); i++) {
                r++;
                String a = "A" + r;
                main.row(r).text(0, answer.texts("GROUP")[i], "normal")
                        .formula(1, "SUMIFS(" + one + "," + grp + "," + a + "," + wgt + ",\">=\"&$B$1)", (double) answer.integers("COUNT")[i], "int")
                        .formula(2, "SUMIFS(" + wgt + "," + grp + "," + a + "," + wgt + ",\">=\"&$B$1)", answer.numbers("WEIGHT")[i], "decimal")
                        .formula(3, "SUMIFS(" + vw + "," + grp + "," + a + "," + wgt + ",\">=\"&$B$1)/C" + r, answer.numbers("MEAN")[i], "decimal");
            }
            if (answer != null && answer.rows() > 0) {
                wb.name("OUT_MEAN_BY_GROUP_MEAN", main.name(), "$D$4");
            }
        }
    }
}
