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

import com.ash.engine.ColumnSchema;
import com.ash.engine.Control;
import com.ash.engine.EngineException;
import com.ash.engine.Filter;
import com.ash.engine.Table;
import com.ash.engine.TableSchema;
import com.ash.engine.Tables;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Per group, in ID order: the count, the sum of weights and the sum of value times weight. The one arithmetic of wmean. */
final class Sums {

    /** The additive aggregates of a partial, in this engine's own layout. */
    static final TableSchema PARTIAL = new TableSchema("sums", List.of(ColumnSchema.of("GROUP", "string", false),
            ColumnSchema.of("COUNT", "integer", false), ColumnSchema.of("WEIGHT", "number", false), ColumnSchema.of("PRODUCT", "number", false)),
            List.of("GROUP"), List.of());

    /** One group's sums. */
    record Group(String name, long count, double weight, double product) {}

    final Map<String, Group> groups;

    private Sums(Map<String, Group> groups) {
        this.groups = groups;
    }

    /** Sums over the rows the filter lets through, with a weight of at least {@code min}; {@code check} is called once per 4,096 rows. */
    static Sums of(Observations rows, Filter see, double min, Control control) throws EngineException {
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, double[]> sums = new TreeMap<>();
        for (int i = 0; i < rows.size(); i++) {
            if ((i & 4095) == 0) {
                control.check();
            }
            String g = rows.group[i];
            if (!see.test(c -> "GROUP".equals(c) ? g : null) || rows.weight[i] < min) {
                continue;
            }
            counts.computeIfAbsent(g, k -> new long[1])[0]++;
            double[] s = sums.computeIfAbsent(g, k -> new double[2]);
            s[0] += rows.weight[i];
            s[1] += rows.value[i] * rows.weight[i];
        }
        Map<String, Group> out = new TreeMap<>();
        sums.forEach((g, s) -> out.put(g, new Group(g, counts.get(g)[0], s[0], s[1])));
        return new Sums(out);
    }

    /** Sums merged from partials: the groups of two shards are disjoint, so nothing is added twice and no bit moves. */
    static Sums merge(List<Table> tables) {
        Map<String, Group> out = new TreeMap<>();
        for (Table t : tables) {
            for (int r = 0; r < t.rows(); r++) {
                Group g = new Group(t.texts("GROUP")[r], t.integers("COUNT")[r], t.numbers("WEIGHT")[r], t.numbers("PRODUCT")[r]);
                if (out.put(g.name(), g) != null) {
                    throw new IllegalArgumentException("the group " + g.name() + " is in two partials: shards are split by GROUP");
                }
            }
        }
        return new Sums(out);
    }

    /** The answer's table: groups of positive total weight. */
    Table table(TableSchema schema) {
        Tables.Builder b = Tables.of(schema);
        groups.values().stream().filter(g -> g.weight() > 0).forEach(g -> b.row(g.name(), g.count(), g.weight(), g.product() / g.weight()));
        return b.build();
    }

    /** The partial's table. */
    Table partial() {
        Tables.Builder b = Tables.of(PARTIAL);
        groups.values().forEach(g -> b.row(g.name(), g.count(), g.weight(), g.product()));
        return b.build();
    }
}
