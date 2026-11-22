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

import com.ash.engine.Batch;
import com.ash.engine.Changes;
import com.ash.engine.Table;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Observations held as arrays sorted by ID: a fixed order of summation, so an answer never depends on how the host delivered the rows.
 * Immutable: a change makes a new copy, so a session swaps it whole and an ask sees the old or the new, never a mix.
 */
final class Observations {

    final String[] id;
    final String[] group;
    final double[] value;
    final double[] weight;

    private Observations(String[] id, String[] group, double[] value, double[] weight) {
        this.id = id;
        this.group = group;
        this.value = value;
        this.weight = weight;
    }

    int size() {
        return id.length;
    }

    /** Bytes held, as the descriptor declares them per row. */
    long bytes() {
        return 40L * id.length;
    }

    /** Collects rows batch by batch (the batch's arrays are the host's and are reused: copied here). */
    static final class Builder {
        private final Map<String, Object[]> rows = new TreeMap<>();

        void add(Batch b) {
            String[] ids = b.texts("ID");
            String[] groups = b.texts("GROUP");
            double[] values = b.numbers("VALUE");
            double[] weights = b.numbers("WEIGHT");
            for (int i = 0; i < b.rows(); i++) {
                if (ids[i] == null || groups[i] == null || b.isNull("VALUE", i) || b.isNull("WEIGHT", i)) {
                    continue;
                }
                rows.put(ids[i], new Object[] {groups[i], values[i], weights[i]});
            }
        }

        Observations build() {
            return of(rows);
        }
    }

    private static Observations of(Map<String, Object[]> sorted) {
        int n = sorted.size();
        String[] id = new String[n];
        String[] group = new String[n];
        double[] value = new double[n];
        double[] weight = new double[n];
        int i = 0;
        for (Map.Entry<String, Object[]> e : sorted.entrySet()) {
            id[i] = e.getKey();
            group[i] = (String) e.getValue()[0];
            value[i] = (Double) e.getValue()[1];
            weight[i] = (Double) e.getValue()[2];
            i++;
        }
        return new Observations(id, group, value, weight);
    }

    private Map<String, Object[]> asMap() {
        Map<String, Object[]> m = new TreeMap<>();
        for (int i = 0; i < id.length; i++) {
            m.put(id[i], new Object[] {group[i], value[i], weight[i]});
        }
        return m;
    }

    /** A new copy with the changes applied: upserts replace rows of the same ID or are added; deleted IDs are removed. */
    Observations with(List<Changes> changes) {
        Map<String, Object[]> m = asMap();
        for (Changes c : changes) {
            c.deletedKeys().forEach(m::remove);
            Builder b = new Builder();
            b.add(c.upserts());
            m.putAll(b.rows);
        }
        return of(m);
    }

    /** A new copy with the what-if rows standing over these (by ID); this copy is unchanged. */
    Observations overlaid(Table whatIf) {
        if (whatIf == null || whatIf.rows() == 0) {
            return this;
        }
        Map<String, Object[]> m = asMap();
        for (int r = 0; r < whatIf.rows(); r++) {
            Object idv = whatIf.value("ID", r);
            Object g = whatIf.value("GROUP", r);
            Object v = whatIf.value("VALUE", r);
            Object w = whatIf.value("WEIGHT", r);
            if (idv == null || g == null || v == null || w == null) {
                continue;
            }
            m.put(idv.toString(), new Object[] {g.toString(), ((Number) v).doubleValue(), ((Number) w).doubleValue()});
        }
        return of(m);
    }

    /** The first {@code n} observations in ID order (the interim answer's sample). */
    Observations head(int n) {
        int k = Math.min(n, id.length);
        return new Observations(Arrays.copyOf(id, k), Arrays.copyOf(group, k), Arrays.copyOf(value, k), Arrays.copyOf(weight, k));
    }

    /** The indices of the rows, ID order, that a predicate on the group keeps. */
    List<Integer> rows(java.util.function.Predicate<String> group) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < id.length; i++) {
            if (group.test(this.group[i])) {
                out.add(i);
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
