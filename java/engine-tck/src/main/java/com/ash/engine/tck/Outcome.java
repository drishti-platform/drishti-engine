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
import com.ash.engine.Missing;
import com.ash.engine.Table;
import com.ash.engine.text.Canonical;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What one ask gave: an answer, a refusal with its code, or a failure. */
public record Outcome(Answer answer, String code, String kind, String words) {

    static Outcome of(Answer a) {
        return new Outcome(a, null, null, null);
    }

    static Outcome refused(String code, String kind, String words) {
        return new Outcome(null, code, kind, words);
    }

    static Outcome failed(Throwable t) {
        return new Outcome(null, "FAILED", "FAILED", t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
    }

    public boolean answered() {
        return answer != null;
    }

    public boolean failed() {
        return "FAILED".equals(code);
    }

    /** Short words for a report. */
    public String brief() {
        if (answer != null) {
            return answer.status() + " " + answer.tables().stream().map(t -> t.schema().name() + "(" + t.rows() + ")").toList()
                    + (answer.missing().isEmpty() ? "" : " missing " + answer.missing().stream().map(Missing::input).toList());
        }
        return code + " " + words;
    }

    /** The outcome as plain data (numbers as raw bits; no stamp): what results files hash. */
    public Map<String, Object> plain() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (answer == null) {
            m.put("refused", code);
            return m;
        }
        m.put("status", answer.status().name());
        List<Object> tables = new ArrayList<>();
        for (Table t : answer.tables()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("output", t.schema().name());
            List<String> cols = new ArrayList<>();
            for (ColumnSchema c : t.schema().columns()) {
                cols.add(c.name() + ":" + c.type());
            }
            tm.put("columns", cols);
            List<Object> rows = new ArrayList<>();
            for (int r = 0; r < t.rows(); r++) {
                List<Object> row = new ArrayList<>();
                for (ColumnSchema c : t.schema().columns()) {
                    Object v = t.value(c.name(), r);
                    row.add(v instanceof Double d ? Canonical.bits(d) : v instanceof double[] a ? bits(a) : v);
                }
                rows.add(row);
            }
            tm.put("rows", rows);
            tables.add(tm);
        }
        m.put("tables", tables);
        List<Object> missing = new ArrayList<>();
        answer.missing().forEach(x -> missing.add(Map.of("input", x.input(), "columns", x.columns(), "outputs", x.outputs())));
        m.put("missing", missing);
        return m;
    }

    /** SHA-384 of {@link #plain()}: equal outcomes, equal hashes. */
    public String hash() {
        return Canonical.sha384(Canonical.json(plain())).substring(0, 32);
    }

    private static List<String> bits(double[] a) {
        List<String> out = new ArrayList<>();
        for (double d : a) {
            out.add(Canonical.bits(d));
        }
        return out;
    }
}
