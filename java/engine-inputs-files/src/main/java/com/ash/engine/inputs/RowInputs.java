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
package com.ash.engine.inputs;

import com.ash.engine.Aggregate;
import com.ash.engine.Batch;
import com.ash.engine.BatchSink;
import com.ash.engine.Descriptor;
import com.ash.engine.EngineException;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.Filter;
import com.ash.engine.GroupScan;
import com.ash.engine.InputTable;
import com.ash.engine.Inputs;
import com.ash.engine.Scan;
import com.ash.engine.VectorScan;
import com.ash.engine.VectorSink;
import com.ash.engine.text.Canonical;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link Inputs} over rows held in memory (maps of column to value, as read from JSONL or a kit case), delivered as a host would, under the variation a law asks for (batch size, order,
 * the viewer's rows only, push-down offered or refused). It records every column asked and refuses an undeclared one
 * ({@code ENG-2003}). Arrays of a batch are reused and overwritten after the sink returns, so an engine that keeps them is caught.
 */
public final class RowInputs implements Inputs {

    /** How rows are delivered. */
    public enum Order { NATURAL, REVERSED, SHUFFLED }

    /** The host's choices a law varies. {@code viewer}: rows outside these filters are never delivered (read as the viewer). */
    public record Delivery(int batchRows, Order order, long shuffleSeed, Map<String, Filter> viewer, boolean pushdown) {

        /** The plain delivery: the engine's batch size, natural order, every row, push-down offered. */
        public static final Delivery PLAIN = new Delivery(0, Order.NATURAL, 0, Map.of(), true);

        /** The same delivery with grouped scans refused. */
        public Delivery withoutPushdown() {
            return new Delivery(batchRows, order, shuffleSeed, viewer, false);
        }
    }

    /** What the engine read: {@code input.COLUMN}, and undeclared asks. */
    public static final class Recorder {
        private final Set<String> read = ConcurrentHashMap.newKeySet();
        private final Set<String> undeclared = ConcurrentHashMap.newKeySet();

        public Set<String> read() {
            return new TreeSet<>(read);
        }

        public Set<String> undeclared() {
            return new TreeSet<>(undeclared);
        }
    }

    private final Descriptor descriptor;
    private final Map<String, Table> tables = new TreeMap<>();
    private final Map<String, Map<String, Table>> open = new TreeMap<>();

    /**
     * @param rows per input its rows (a null list: the table is absent)
     * @param openTables per open input, its tables by name
     */
    public RowInputs(Descriptor d, Map<String, List<Map<String, Object>>> rows, Map<String, Map<String, List<Map<String, Object>>>> openTables,
            Delivery delivery, Recorder recorder, Expressions el) {
        this.descriptor = d;
        d.inputs().forEach((name, in) -> {
            if (!in.open()) {
                tables.put(name, new Table(name, in, rows.get(name), rows.containsKey(name) && rows.get(name) != null, delivery, recorder, el));
            } else {
                Map<String, Table> m = new TreeMap<>();
                openTables.getOrDefault(name, Map.of()).forEach((t, r) -> m.put(t, new Table(name + ":" + t, in, r, r != null, delivery, recorder, el)));
                open.put(name, m);
            }
        });
    }

    @Override
    public Set<String> names() {
        return descriptor.inputs().keySet();
    }

    @Override
    public InputTable table(String name) {
        if (!descriptor.inputs().containsKey(name)) {
            throw new IllegalArgumentException("not a declared input: " + name);
        }
        Table t = tables.get(name);
        return t != null ? t : new Table(name, descriptor.inputs().get(name), null, false, Delivery.PLAIN, new Recorder(), null);
    }

    @Override
    public InputTable table(String input, String table) {
        Descriptor.Input in = descriptor.inputs().get(input);
        if (in == null || !in.open()) {
            throw new IllegalArgumentException(input + " is not an open input");
        }
        Table t = open.getOrDefault(input, Map.of()).get(table);
        return t != null ? t : new Table(input + ":" + table, in, null, false, Delivery.PLAIN, new Recorder(), null);
    }

    /** The stamps of the inputs as delivered, for the answer's stamp (open tables as {@code input:table}). */
    public java.util.SortedMap<String, String> stamps() {
        java.util.SortedMap<String, String> out = new TreeMap<>();
        tables.forEach((n, t) -> out.put(n, t.stamp()));
        open.forEach((n, m) -> m.forEach((tn, t) -> out.put(n + ":" + tn, t.stamp())));
        return out;
    }

    /** Text of a cell as a filter reads it. */
    public static String text(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) (double) d);
        }
        return v.toString();
    }

    /** One table of rows. */
    static final class Table implements InputTable {
        private final String name;
        private final Descriptor.Input input;
        private final List<Map<String, Object>> rows;
        private final boolean present;
        private final Delivery delivery;
        private final Recorder recorder;
        private final Expressions el;
        private final String stamp;
        private final Set<String> columns;

        Table(String name, Descriptor.Input input, List<Map<String, Object>> all, boolean present, Delivery delivery, Recorder recorder, Expressions el) {
            this.name = name;
            this.input = input;
            this.present = present;
            this.delivery = delivery;
            this.recorder = recorder;
            this.el = el;
            List<Map<String, Object>> r = new ArrayList<>();
            Filter viewer = delivery.viewer().get(name);
            if (all != null) {
                for (Map<String, Object> row : all) {
                    if (viewer == null || viewer.test(c -> text(row.get(c)))) {
                        r.add(row);
                    }
                }
            }
            this.rows = Collections.unmodifiableList(r);
            this.stamp = present ? Canonical.sha384(Canonical.json(rows)).substring(0, 32) : "absent";
            Set<String> cols = new LinkedHashSet<>();
            if (input.open()) {
                if (all != null) {
                    all.forEach(row -> cols.addAll(row.keySet()));
                }
            } else {
                for (String c : input.columns()) {
                    if (all == null || all.isEmpty() || all.stream().anyMatch(row -> row.containsKey(c))) {
                        cols.add(c);
                    }
                }
            }
            this.columns = Collections.unmodifiableSet(cols);
        }

        @Override public String name() { return name; }
        @Override public boolean present() { return present; }
        @Override public Set<String> columns() { return present ? columns : Set.of(); }
        @Override public String stamp() { return stamp; }
        @Override public long rows() { return present ? rows.size() : -1; }

        private void declared(String c) throws EngineException {
            if (!input.open() && !input.columns().contains(c)) {
                recorder.undeclared.add(name + "." + c);
                throw new EngineException("ENG-2003", Kind.REFUSED, "the column " + c + " is not declared for the input " + name);
            }
            recorder.read.add(name.split(":", 2)[0] + "." + c);
        }

        private List<Map<String, Object>> ordered(Filter where) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                if (where == null || where.test(c -> text(r.get(c)))) {
                    out.add(r);
                }
            }
            switch (delivery.order()) {
                case REVERSED -> Collections.reverse(out);
                case SHUFFLED -> Collections.shuffle(out, new Random(delivery.shuffleSeed()));
                default -> { }
            }
            return out;
        }

        private int batchSize(int asked) {
            int n = delivery.batchRows() > 0 ? delivery.batchRows() : asked;
            return Math.max(1, n);
        }

        @Override
        public void scan(Scan scan, BatchSink sink) throws EngineException {
            for (String c : scan.columns()) {
                declared(c);
            }
            if (!present) {
                return;
            }
            List<Map<String, Object>> rs = ordered(scan.where());
            int size = batchSize(scan.batchRows());
            RowBatch b = new RowBatch(scan.columns(), size);
            for (int from = 0; from < rs.size(); from += size) {
                b.fill(rs.subList(from, Math.min(rs.size(), from + size)));
                sink.batch(b);
                b.poison();
            }
        }

        @Override
        public void vectors(VectorScan scan, VectorSink sink) throws EngineException {
            declared(scan.column());
            for (String c : scan.texts()) {
                declared(c);
            }
            if (!present) {
                return;
            }
            double[] values = new double[0];
            String[] texts = new String[scan.texts().size()];
            int i = 0;
            for (Map<String, Object> r : ordered(scan.where())) {
                Object v = r.get(scan.column());
                int len = -1;
                if (v instanceof List<?> l) {
                    if (values.length < l.size()) {
                        values = new double[l.size()];
                    }
                    for (int k = 0; k < l.size(); k++) {
                        values[k] = ((Number) l.get(k)).doubleValue();
                    }
                    len = l.size();
                }
                for (int t = 0; t < texts.length; t++) {
                    texts[t] = text(r.get(scan.texts().get(t)));
                }
                String id = input.key().isEmpty() ? Integer.toString(i) : String.join("|", input.key().stream().map(k -> text(r.get(k))).toList());
                sink.row(id, values, len, texts);
                java.util.Arrays.fill(values, Double.NaN);
                i++;
            }
        }

        @Override
        public boolean offers(GroupScan scan) {
            if (!delivery.pushdown() || el == null || !present) {
                return false;
            }
            Set<String> cols = input.open() ? columns : new TreeSet<>(input.columns());
            return scan.sets().stream().flatMap(List::stream).allMatch(cols::contains);
        }

        @Override
        public void groups(GroupScan scan, BatchSink sink) throws EngineException {
            if (!offers(scan)) {
                throw new EngineException("ENG-2003", Kind.REFUSED, "a grouped scan that was not offered was asked of " + name);
            }
            for (List<String> set : scan.sets()) {
                for (String c : set) {
                    declared(c);
                }
            }
            List<Map<String, Object>> rs = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                if (scan.where() == null || truthy(el.row(scan.where(), r))) {
                    rs.add(r);
                }
            }
            for (List<String> set : scan.sets()) {
                TreeMap<List<String>, Object[]> groups = new TreeMap<>((a, b) -> {
                    for (int i = 0; i < a.size(); i++) {
                        int c = String.valueOf(a.get(i)).compareTo(String.valueOf(b.get(i)));
                        if (c != 0) {
                            return c;
                        }
                    }
                    return 0;
                });
                for (Map<String, Object> r : rs) {
                    List<String> key = set.stream().map(c -> text(r.get(c))).toList();
                    Object[] states = groups.computeIfAbsent(key, k -> new Object[scan.aggregates().size()]);
                    for (int a = 0; a < states.length; a++) {
                        Aggregate ag = scan.aggregates().get(a);
                        if (ag.where() != null && !truthy(el.row(ag.where(), r))) {
                            continue;
                        }
                        Object x = ag.fn() == Aggregate.Fn.COUNT ? (Object) 1L : el.row(ag.of(), r);
                        states[a] = merge(ag.fn(), states[a], x);
                    }
                }
                List<String> cols = new ArrayList<>(set);
                scan.aggregates().forEach(a -> cols.add(a.name()));
                List<Map<String, Object>> out = new ArrayList<>();
                groups.forEach((k, st) -> {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 0; i < set.size(); i++) {
                        row.put(set.get(i), k.get(i));
                    }
                    for (int a = 0; a < st.length; a++) {
                        Aggregate ag = scan.aggregates().get(a);
                        Object v = st[a];
                        row.put(ag.name(), v instanceof BigDecimal bd ? (Object) bd.doubleValue() : v == null && ag.fn() == Aggregate.Fn.COUNT ? 0L : v);
                    }
                    out.add(row);
                });
                int size = batchSize(scan.batchRows() <= 0 ? 4096 : scan.batchRows());
                RowBatch b = new RowBatch(cols, size);
                for (int from = 0; from < out.size(); from += size) {
                    b.fill(out.subList(from, Math.min(out.size(), from + size)));
                    sink.batch(b);
                    b.poison();
                }
            }
        }

        /** The kit's exact executor: integers as longs, floating-point sums exactly (BigDecimal), rounded once. */
        private static Object merge(Aggregate.Fn fn, Object state, Object x) {
            if (x == null) {
                return state;
            }
            if (fn == Aggregate.Fn.COUNT) {
                return state == null ? 1L : (Long) state + 1;
            }
            if (!(x instanceof Number n)) {
                throw new IllegalArgumentException("an aggregate of a value that is not a number: " + x);
            }
            boolean whole = x instanceof Long || x instanceof Integer;
            switch (fn) {
                case SUM -> {
                    if (state == null) {
                        return whole ? (Object) n.longValue() : new BigDecimal(n.doubleValue());
                    }
                    if (state instanceof Long l && whole) {
                        return Math.addExact(l, n.longValue());
                    }
                    BigDecimal s = state instanceof Long l ? BigDecimal.valueOf(l) : (BigDecimal) state;
                    return s.add(whole ? BigDecimal.valueOf(n.longValue()) : new BigDecimal(n.doubleValue()));
                }
                case MIN, MAX -> {
                    if (state == null) {
                        return x;
                    }
                    double a = ((Number) state).doubleValue();
                    double b = n.doubleValue();
                    return fn == Aggregate.Fn.MIN ? (b < a ? x : state) : (b > a ? x : state);
                }
                default -> throw new IllegalStateException();
            }
        }

        private static boolean truthy(Object v) {
            return v instanceof Boolean b ? b : v != null;
        }
    }

    /** A batch whose arrays are allocated once and reused; poisoned after each delivery. */
    public static final class RowBatch implements Batch {
        private final List<String> columns;
        private final int capacity;
        private List<Map<String, Object>> rows = List.of();
        private final Map<String, Object> arrays = new HashMap<>();
        private int n;

        public RowBatch(List<String> columns, int capacity) {
            this.columns = List.copyOf(columns);
            this.capacity = capacity;
        }

        public void fill(List<Map<String, Object>> rs) {
            rows = rs;
            n = rs.size();
            arrays.clear();
        }

        public void poison() {
            for (Object a : arrays.values()) {
                if (a instanceof double[] d) {
                    java.util.Arrays.fill(d, Double.NaN);
                } else if (a instanceof long[] l) {
                    java.util.Arrays.fill(l, Long.MIN_VALUE);
                } else if (a instanceof String[] s) {
                    java.util.Arrays.fill(s, "\u0000poisoned");
                }
            }
            reuse.putAll(arrays);
        }

        private final Map<String, Object> reuse = new HashMap<>();

        private void known(String c) {
            if (!columns.contains(c)) {
                throw new IllegalArgumentException("the column " + c + " was not asked in this scan; asked: " + columns);
            }
        }

        @Override
        public int rows() {
            return n;
        }

        @Override
        public String[] texts(String c) {
            known(c);
            String[] a = reuse.get(c + "#t") instanceof String[] s ? s : new String[capacity];
            for (int i = 0; i < n; i++) {
                Object v = rows.get(i).get(c);
                if (v instanceof List<?>) {
                    throw new IllegalArgumentException(c + " holds vectors: read it with vectors()");
                }
                a[i] = text(v);
            }
            arrays.put(c + "#t", a);
            return a;
        }

        @Override
        public long[] integers(String c) {
            known(c);
            long[] a = reuse.get(c + "#i") instanceof long[] l ? l : new long[capacity];
            for (int i = 0; i < n; i++) {
                Object v = rows.get(i).get(c);
                if (v == null) {
                    a[i] = 0;
                } else if (v instanceof Long || v instanceof Integer) {
                    a[i] = ((Number) v).longValue();
                } else if (v instanceof Double d && d == Math.rint(d)) {
                    a[i] = (long) (double) d;
                } else {
                    throw new IllegalArgumentException(c + " is not an integer column: " + v);
                }
            }
            arrays.put(c + "#i", a);
            return a;
        }

        @Override
        public double[] numbers(String c) {
            known(c);
            double[] a = reuse.get(c + "#n") instanceof double[] d ? d : new double[capacity];
            for (int i = 0; i < n; i++) {
                Object v = rows.get(i).get(c);
                if (v == null) {
                    a[i] = Double.NaN;
                } else if (v instanceof Number num) {
                    a[i] = num.doubleValue();
                } else if (v instanceof String s && s.startsWith("0x") && s.length() == 18) {
                    a[i] = Double.longBitsToDouble(Long.parseUnsignedLong(s.substring(2), 16));
                } else {
                    throw new IllegalArgumentException(c + " is not a number column: " + v);
                }
            }
            arrays.put(c + "#n", a);
            return a;
        }

        @Override
        public boolean[] flags(String c) {
            known(c);
            boolean[] a = new boolean[capacity];
            for (int i = 0; i < n; i++) {
                a[i] = Boolean.TRUE.equals(rows.get(i).get(c));
            }
            return a;
        }

        @Override
        public boolean isNull(String c, int row) {
            known(c);
            return rows.get(row).get(c) == null;
        }
    }
}
