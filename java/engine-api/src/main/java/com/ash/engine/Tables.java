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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds tables: row by row, or whole columns at once (arrays taken, not copied). */
public final class Tables {

    private Tables() {
    }

    /** A builder for a table of {@code schema}. */
    public static Builder of(TableSchema schema) {
        return new ColumnTable.Maker(schema);
    }

    /** An empty table of {@code schema}. */
    public static Table empty(TableSchema schema) {
        return of(schema).build();
    }

    /** Builds one table. */
    public interface Builder {

        /** One row, values in column order: String, a Number, Boolean, double[] or null. */
        Builder row(Object... valuesInColumnOrder);

        /**
         * Whole columns at once; arrays are taken, not copied. Carriers: {@code String[]}; {@code long[]} or {@code Long[]}
         * (null entries are nulls); {@code double[]} (NaN is null) or {@code Double[]}; {@code boolean[]} or {@code Boolean[]};
         * {@code double[][]} (a null entry is null). Every column of the schema is given, all of one length.
         */
        Builder columns(Map<String, Object> arraysByColumn);

        /** Sorts by the schema's {@code order}, checks key uniqueness, types and nullability; IllegalArgumentException in words. */
        Table build();
    }

    /** The table as rows of objects, in column order (for words, tests and wire forms; not for hot paths). */
    public static List<Object[]> rows(Table t) {
        List<ColumnSchema> cols = t.schema().columns();
        List<Object[]> out = new ArrayList<>(t.rows());
        for (int r = 0; r < t.rows(); r++) {
            Object[] row = new Object[cols.size()];
            for (int c = 0; c < cols.size(); c++) {
                row[c] = t.value(cols.get(c).name(), r);
            }
            out.add(row);
        }
        return out;
    }

    /** Column-array storage with null flags. */
    static final class ColumnTable implements Table {
        private final TableSchema schema;
        private final int rows;
        private final Map<String, Object> data;
        private final Map<String, boolean[]> nulls;

        ColumnTable(TableSchema schema, int rows, Map<String, Object> data, Map<String, boolean[]> nulls) {
            this.schema = schema;
            this.rows = rows;
            this.data = data;
            this.nulls = nulls;
        }

        @Override public TableSchema schema() { return schema; }
        @Override public int rows() { return rows; }
        @Override public String[] texts(String c) { return (String[]) get(c, ColumnSchema.Carrier.TEXT); }
        @Override public long[] integers(String c) { return (long[]) get(c, ColumnSchema.Carrier.INTEGER); }
        @Override public double[] numbers(String c) { return (double[]) get(c, ColumnSchema.Carrier.NUMBER); }
        @Override public boolean[] flags(String c) { return (boolean[]) get(c, ColumnSchema.Carrier.FLAG); }
        @Override public double[][] lists(String c) { return (double[][]) get(c, ColumnSchema.Carrier.LIST); }

        @Override public boolean isNull(String c, int row) {
            if (schema.column(c) == null) {
                throw new IllegalArgumentException(schema.name() + " has no column " + c);
            }
            boolean[] n = nulls.get(c);
            return n != null && n[row];
        }

        private Object get(String c, ColumnSchema.Carrier want) {
            ColumnSchema s = schema.column(c);
            if (s == null) {
                throw new IllegalArgumentException(schema.name() + " has no column " + c);
            }
            if (s.carrier() != want) {
                throw new IllegalArgumentException(schema.name() + "." + c + " is of type " + s.type() + ", not read as " + want.name().toLowerCase());
            }
            return data.get(c);
        }

        static final class Maker implements Builder {
            private final TableSchema schema;
            private final List<Object[]> rows = new ArrayList<>();
            private Map<String, Object> columns;

            Maker(TableSchema schema) {
                this.schema = schema;
            }

            @Override public Builder row(Object... values) {
                if (columns != null) {
                    throw new IllegalStateException("rows and whole columns are not mixed");
                }
                if (values.length != schema.columns().size()) {
                    throw new IllegalArgumentException(schema.name() + ": a row of " + values.length + " values for " + schema.columns().size() + " columns");
                }
                rows.add(values.clone());
                return this;
            }

            @Override public Builder columns(Map<String, Object> arrays) {
                if (!rows.isEmpty() || columns != null) {
                    throw new IllegalStateException("whole columns are given once, without rows");
                }
                columns = new HashMap<>(arrays);
                return this;
            }

            @Override public Table build() {
                int n;
                Map<String, Object> data = new HashMap<>();
                Map<String, boolean[]> nulls = new HashMap<>();
                if (columns != null) {
                    n = -1;
                    for (ColumnSchema c : schema.columns()) {
                        Object a = columns.get(c.name());
                        if (a == null) {
                            throw new IllegalArgumentException(schema.name() + ": no array for column " + c.name());
                        }
                        int len = java.lang.reflect.Array.getLength(a);
                        if (n >= 0 && len != n) {
                            throw new IllegalArgumentException(schema.name() + ": column " + c.name() + " has " + len + " rows, others " + n);
                        }
                        n = len;
                        boolean[] nl = new boolean[len];
                        data.put(c.name(), carrier(c, a, nl));
                        nulls.put(c.name(), nl);
                    }
                    if (columns.size() != schema.columns().size()) {
                        throw new IllegalArgumentException(schema.name() + ": arrays for columns the schema does not have");
                    }
                    n = Math.max(n, 0);
                } else {
                    n = rows.size();
                    for (int ci = 0; ci < schema.columns().size(); ci++) {
                        ColumnSchema c = schema.columns().get(ci);
                        Object[] boxed = new Object[n];
                        for (int r = 0; r < n; r++) {
                            boxed[r] = rows.get(r)[ci];
                        }
                        boolean[] nl = new boolean[n];
                        data.put(c.name(), carrier(c, boxed, nl));
                        nulls.put(c.name(), nl);
                    }
                }
                for (ColumnSchema c : schema.columns()) {
                    boolean[] nl = nulls.get(c.name());
                    for (int r = 0; r < n; r++) {
                        if (nl[r] && !c.nullable()) {
                            throw new IllegalArgumentException(schema.name() + "." + c.name() + " is not nullable and row " + (r + 1) + " has no value");
                        }
                    }
                }
                ColumnTable unsorted = new ColumnTable(schema, n, data, nulls);
                return sortedAndChecked(unsorted);
            }

            private Table sortedAndChecked(ColumnTable t) {
                int n = t.rows();
                Integer[] idx = new Integer[n];
                for (int i = 0; i < n; i++) {
                    idx[i] = i;
                }
                Comparator<Integer> cmp = (a, b) -> 0;
                for (String o : schema.order()) {
                    cmp = cmp.thenComparing((a, b) -> compare(t, o, a, b));
                }
                Arrays.sort(idx, cmp);
                boolean identity = true;
                for (int i = 0; i < n; i++) {
                    identity &= idx[i] == i;
                }
                ColumnTable out = identity ? t : permute(t, idx);
                if (!schema.key().isEmpty()) {
                    Set<List<Object>> seen = new HashSet<>();
                    for (int r = 0; r < n; r++) {
                        List<Object> k = new ArrayList<>();
                        for (String kc : schema.key()) {
                            Object v = out.value(kc, r);
                            k.add(v instanceof double[] d ? Arrays.toString(d) : v);
                        }
                        if (!seen.add(k)) {
                            throw new IllegalArgumentException(schema.name() + ": the key " + schema.key() + " = " + k + " is not unique");
                        }
                    }
                }
                return out;
            }

            private ColumnTable permute(ColumnTable t, Integer[] idx) {
                Map<String, Object> data = new HashMap<>();
                Map<String, boolean[]> nulls = new HashMap<>();
                for (ColumnSchema c : schema.columns()) {
                    Object src = t.data.get(c.name());
                    Object dst = java.lang.reflect.Array.newInstance(src.getClass().getComponentType(), idx.length);
                    boolean[] sn = t.nulls.get(c.name());
                    boolean[] dn = new boolean[idx.length];
                    for (int i = 0; i < idx.length; i++) {
                        java.lang.reflect.Array.set(dst, i, java.lang.reflect.Array.get(src, idx[i]));
                        dn[i] = sn[idx[i]];
                    }
                    data.put(c.name(), dst);
                    nulls.put(c.name(), dn);
                }
                return new ColumnTable(schema, idx.length, data, nulls);
            }

            private static int compare(ColumnTable t, String col, int a, int b) {
                boolean na = t.isNull(col, a);
                boolean nb = t.isNull(col, b);
                if (na || nb) {
                    return na && nb ? 0 : na ? -1 : 1;                              // nulls first
                }
                return switch (t.schema().column(col).carrier()) {
                    case TEXT -> compareText(t.schema().column(col).type(), t.texts(col)[a], t.texts(col)[b]);
                    case INTEGER -> Long.compare(t.integers(col)[a], t.integers(col)[b]);
                    case NUMBER -> Double.compare(t.numbers(col)[a], t.numbers(col)[b]);
                    case FLAG -> Boolean.compare(t.flags(col)[a], t.flags(col)[b]);
                    case LIST -> throw new IllegalArgumentException("a list column cannot order rows: " + col);
                };
            }

            private static int compareText(String type, String a, String b) {
                if ("decimal".equals(type)) {
                    return new java.math.BigDecimal(a).compareTo(new java.math.BigDecimal(b));
                }
                return a.compareTo(b);
            }

            private Object carrier(ColumnSchema c, Object a, boolean[] nl) {
                int len = java.lang.reflect.Array.getLength(a);
                String where = schema.name() + "." + c.name();
                switch (c.carrier()) {
                    case TEXT -> {
                        if (a instanceof String[] s) {
                            for (int i = 0; i < len; i++) {
                                nl[i] = s[i] == null;
                            }
                            return s;
                        }
                        String[] s = new String[len];
                        for (int i = 0; i < len; i++) {
                            Object v = java.lang.reflect.Array.get(a, i);
                            nl[i] = v == null;
                            if (v != null && !(v instanceof String)) {
                                throw new IllegalArgumentException(where + " holds text; row " + (i + 1) + " is " + v.getClass().getSimpleName());
                            }
                            s[i] = (String) v;
                        }
                        return s;
                    }
                    case INTEGER -> {
                        if (a instanceof long[] l) {
                            return l;
                        }
                        long[] l = new long[len];
                        for (int i = 0; i < len; i++) {
                            Object v = java.lang.reflect.Array.get(a, i);
                            nl[i] = v == null;
                            if (v != null) {
                                if (!(v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte)) {
                                    throw new IllegalArgumentException(where + " holds integers; row " + (i + 1) + " is " + v);
                                }
                                l[i] = ((Number) v).longValue();
                            }
                        }
                        return l;
                    }
                    case NUMBER -> {
                        if (a instanceof double[] d) {
                            for (int i = 0; i < len; i++) {
                                nl[i] = Double.isNaN(d[i]);
                            }
                            return d;
                        }
                        double[] d = new double[len];
                        for (int i = 0; i < len; i++) {
                            Object v = java.lang.reflect.Array.get(a, i);
                            nl[i] = v == null;
                            if (v != null && !(v instanceof Number)) {
                                throw new IllegalArgumentException(where + " holds numbers; row " + (i + 1) + " is " + v);
                            }
                            d[i] = v == null ? Double.NaN : ((Number) v).doubleValue();
                            if (v != null && (Double.isNaN(d[i]) || Double.isInfinite(d[i]))) {
                                throw new IllegalArgumentException(where + ": row " + (i + 1) + " is " + d[i] + " (NaN and infinities are not figures)");
                            }
                        }
                        return d;
                    }
                    case FLAG -> {
                        if (a instanceof boolean[] f) {
                            return f;
                        }
                        boolean[] f = new boolean[len];
                        for (int i = 0; i < len; i++) {
                            Object v = java.lang.reflect.Array.get(a, i);
                            nl[i] = v == null;
                            f[i] = Boolean.TRUE.equals(v);
                        }
                        return f;
                    }
                    case LIST -> {
                        double[][] out = new double[len][];
                        for (int i = 0; i < len; i++) {
                            Object v = java.lang.reflect.Array.get(a, i);
                            nl[i] = v == null;
                            out[i] = (double[]) v;
                        }
                        return out;
                    }
                    default -> throw new IllegalStateException();
                }
            }
        }
    }
}
