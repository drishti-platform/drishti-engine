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
package com.ash.engine.tck;

import com.ash.engine.Answer;
import com.ash.engine.ColumnSchema;
import com.ash.engine.Descriptor;
import com.ash.engine.Missing;
import com.ash.engine.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** The kit's comparisons: two outcomes under each column's exactness class, and an outcome against a case's {@code expect}. */
public final class Compare {

    private Compare() {
    }

    /** Null when {@code a} and {@code b} are the same under the columns' {@code exact} classes; otherwise the first difference in words. */
    public static String same(Outcome a, Outcome b) {
        return same(a, b, Set.of());
    }

    /** As {@link #same(Outcome, Outcome)}, leaving out the outputs named in {@code skip}. */
    public static String same(Outcome a, Outcome b, Set<String> skip) {
        if (a.answered() != b.answered()) {
            return "one answered, the other did not: " + a.brief() + " | " + b.brief();
        }
        if (!a.answered()) {
            return a.code().equals(b.code()) ? null : "refused with " + a.code() + " and with " + b.code() + " (" + b.words() + ")";
        }
        Answer x = a.answer();
        Answer y = b.answer();
        if (skip.isEmpty() && x.status() != y.status()) {
            return "status " + x.status() + " and " + y.status();
        }
        List<Table> tx = x.tables().stream().filter(t -> !skip.contains(t.schema().name())).toList();
        List<Table> ty = y.tables().stream().filter(t -> !skip.contains(t.schema().name())).toList();
        if (tx.size() != ty.size()) {
            return tx.size() + " tables and " + ty.size();
        }
        for (int i = 0; i < tx.size(); i++) {
            String d = tables(tx.get(i), ty.get(i));
            if (d != null) {
                return d;
            }
        }
        Set<String> mx = new TreeSet<>(x.missing().stream().filter(m -> !skip.containsAll(m.outputs()) || m.outputs().isEmpty()).map(Missing::input).toList());
        Set<String> my = new TreeSet<>(y.missing().stream().filter(m -> !skip.containsAll(m.outputs()) || m.outputs().isEmpty()).map(Missing::input).toList());
        return skip.isEmpty() && !mx.equals(my) ? "missing " + mx + " and " + my : null;
    }

    /** Null when the two tables are equal cell for cell under each column's class. */
    public static String tables(Table a, Table b) {
        String n = a.schema().name();
        if (!n.equals(b.schema().name())) {
            return "tables " + n + " and " + b.schema().name();
        }
        if (!a.schema().columns().equals(b.schema().columns())) {
            return n + ": the columns differ";
        }
        if (a.rows() != b.rows()) {
            return n + ": " + a.rows() + " rows and " + b.rows();
        }
        for (ColumnSchema c : a.schema().columns()) {
            for (int r = 0; r < a.rows(); r++) {
                String d = cell(c, a.value(c.name(), r), b.value(c.name(), r));
                if (d != null) {
                    return n + " row " + (r + 1) + " " + c.name() + ": " + d;
                }
            }
        }
        return null;
    }

    static String cell(ColumnSchema c, Object x, Object y) {
        if (x == null || y == null) {
            return x == y ? null : x + " and " + y;
        }
        if (x instanceof Double dx && y instanceof Double dy) {
            return numbers(c.exact(), dx, dy);
        }
        if (x instanceof double[] ax && y instanceof double[] ay) {
            if (ax.length != ay.length) {
                return "vectors of " + ax.length + " and " + ay.length;
            }
            for (int i = 0; i < ax.length; i++) {
                String d = numbers(c.exact(), ax[i], ay[i]);
                if (d != null) {
                    return "element " + i + ": " + d;
                }
            }
            return null;
        }
        return x.equals(y) ? null : x + " and " + y;
    }

    /** Null when equal under {@code exact} ({@code bits} by default; {@code relative:x}). */
    static String numbers(String exact, double x, double y) {
        if (exact != null && exact.startsWith("relative:")) {
            double tol = Double.parseDouble(exact.substring(9));
            double scale = Math.max(Math.abs(x), Math.abs(y));
            return Math.abs(x - y) <= tol * scale ? null : x + " and " + y + " (beyond " + exact + ")";
        }
        return Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y) ? null
                : x + " and " + y + " (bits " + com.ash.engine.text.Canonical.bits(x) + " and " + com.ash.engine.text.Canonical.bits(y) + ")";
    }

    /** Null when {@code o} is what the case expects; otherwise the differences in words. */
    @SuppressWarnings("unchecked")
    public static String expected(Cases.Case c, Outcome o, Descriptor d) {
        Map<String, Object> e = c.expect();
        if (e.containsKey("refused")) {
            String code = String.valueOf(e.get("refused"));
            if (o.answered()) {
                return "expected the refusal " + code + "; got " + o.brief();
            }
            return code.equals(o.code()) ? null : "expected the refusal " + code + "; got " + o.code() + " " + o.words();
        }
        if (!o.answered()) {
            return "expected " + e.get("status") + "; got " + o.brief();
        }
        String status = String.valueOf(e.get("status")).toUpperCase().replace('-', '_');
        if (!status.equals(o.answer().status().name())) {
            return "expected the status " + e.get("status") + "; got " + o.brief();
        }
        List<String> problems = new ArrayList<>();
        Map<String, Object> tables = e.get("tables") == null ? Map.of() : (Map<String, Object>) e.get("tables");
        for (Map.Entry<String, Object> te : tables.entrySet()) {
            Table t = o.answer().table(te.getKey());
            if (t == null) {
                problems.add("no table " + te.getKey());
                continue;
            }
            Map<String, Object> spec = (Map<String, Object>) te.getValue();
            Object colSpec = spec.get("columns");
            List<String> cols = colSpec instanceof List<?> l ? (List<String>) l : t.schema().columns().stream().map(ColumnSchema::name).toList();
            boolean subset = Boolean.TRUE.equals(spec.get("subset"));
            if (!subset && colSpec instanceof List<?> && !cols.equals(t.schema().columns().stream().map(ColumnSchema::name).toList())) {
                problems.add(te.getKey() + ": columns " + t.schema().columns().stream().map(ColumnSchema::name).toList() + ", the case lists " + cols);
                continue;
            }
            List<List<Object>> rows = spec.get("rows") == null ? List.of() : (List<List<Object>>) spec.get("rows");
            if (rows.size() != t.rows()) {
                problems.add(te.getKey() + ": " + t.rows() + " rows, the case expects " + rows.size());
                continue;
            }
            for (int r = 0; r < rows.size(); r++) {
                for (int i = 0; i < cols.size(); i++) {
                    ColumnSchema cs = t.schema().column(cols.get(i));
                    if (cs == null) {
                        problems.add(te.getKey() + ": no column " + cols.get(i));
                        break;
                    }
                    String diff = expectedCell(cs, rows.get(r).get(i), t.value(cs.name(), r));
                    if (diff != null) {
                        problems.add(te.getKey() + " row " + (r + 1) + " " + cs.name() + ": " + diff);
                    }
                }
            }
        }
        List<Object> missing = e.get("missing") == null ? List.of() : (List<Object>) e.get("missing");
        Set<String> want = new TreeSet<>();
        for (Object m : missing) {
            Map<String, Object> mm = (Map<String, Object>) m;
            want.add(String.valueOf(mm.get("input")));
            Missing got = o.answer().missing().stream().filter(x -> x.input().equals(mm.get("input"))).findFirst().orElse(null);
            if (got != null && mm.get("outputs") != null && !new TreeSet<>((List<String>) mm.get("outputs")).equals(new TreeSet<>(got.outputs()))) {
                problems.add("missing " + got.input() + ": outputs " + got.outputs() + ", the case expects " + mm.get("outputs"));
            }
            if (got != null && mm.get("columns") != null && !new TreeSet<>((List<String>) mm.get("columns")).equals(new TreeSet<>(got.columns()))) {
                problems.add("missing " + got.input() + ": columns " + got.columns() + ", the case expects " + mm.get("columns"));
            }
        }
        Set<String> got = new TreeSet<>(o.answer().missing().stream().map(Missing::input).toList());
        if (!want.equals(got)) {
            problems.add("missing inputs " + got + ", the case expects " + want);
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    @SuppressWarnings("unchecked")
    static String expectedCell(ColumnSchema c, Object want, Object got) {
        if (want == null || got == null) {
            return want == got ? null : "got " + got + ", the case expects " + want;
        }
        switch (c.carrier()) {
            case NUMBER -> {
                double w = number(want);
                return numbers(c.exact(), w, (Double) got) == null ? null : "got " + got + " (" + com.ash.engine.text.Canonical.bits((Double) got)
                        + "), the case expects " + want + " (" + com.ash.engine.text.Canonical.bits(w) + ")";
            }
            case INTEGER -> {
                return want instanceof Number n && n.longValue() == (Long) got && !(want instanceof Double dd && dd != Math.rint(dd)) ? null
                        : "got " + got + ", the case expects " + want;
            }
            case TEXT -> {
                if ("decimal".equals(c.type()) && want instanceof Number) {
                    return new BigDecimal(want.toString()).compareTo(new BigDecimal((String) got)) == 0 ? null : "got " + got + ", the case expects " + want;
                }
                return String.valueOf(want).equals(got) ? null : "got " + got + ", the case expects " + want;
            }
            case FLAG -> {
                return want.equals(got) ? null : "got " + got + ", the case expects " + want;
            }
            case LIST -> {
                List<Object> wl = (List<Object>) want;
                double[] g = (double[]) got;
                if (wl.size() != g.length) {
                    return "a vector of " + g.length + ", the case expects " + wl.size();
                }
                for (int i = 0; i < g.length; i++) {
                    if (numbers(c.exact(), number(wl.get(i)), g[i]) != null) {
                        return "element " + i + " is " + g[i] + ", the case expects " + wl.get(i);
                    }
                }
                return null;
            }
            default -> throw new IllegalStateException();
        }
    }

    /** A case's number: a YAML number, or {@code "0x…"} (16 hex digits) for the bits. */
    static double number(Object v) {
        if (v instanceof String s && s.startsWith("0x") && s.length() == 18) {
            return Double.longBitsToDouble(Long.parseUnsignedLong(s.substring(2), 16));
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("not a number: " + v);
    }
}
