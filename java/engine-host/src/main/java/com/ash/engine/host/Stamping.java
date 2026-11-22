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
package com.ash.engine.host;

import com.ash.engine.ColumnSchema;
import com.ash.engine.Request;
import com.ash.engine.Snapshot;
import com.ash.engine.Table;
import com.ash.engine.Tables;
import com.ash.engine.text.Canonical;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The hashes a host puts in a stamp: the request (a {@code table} parameter by the hash of its rows) and the options. */
public final class Stamping {

    private Stamping() {
    }

    /** SHA-384 of the request's canonical form: snapshot, outputs, parameters, visibility signature. The seed is its own field. */
    public static String request(Snapshot snapshot, Request r) {
        Map<String, Object> m = new TreeMap<>();
        m.put("snapshot", snapshot.dimensions());
        m.put("outputs", r.outputs());
        Map<String, Object> p = new TreeMap<>();
        r.params().forEach((k, v) -> p.put(k, v instanceof Table t ? "table:" + table(t) : v));
        m.put("params", p);
        m.put("visibility", r.visibility() == null ? null : r.visibility().signature());
        return Canonical.sha384(Canonical.json(m));
    }

    /** SHA-384 of options' canonical form. */
    public static String options(Map<String, Object> options) {
        return Canonical.sha384(Canonical.json(options));
    }

    /** SHA-384 of a table's canonical form (columns and types, rows, numbers as raw bits): how a table parameter enters a stamp. */
    public static String table(Table t) {
        return Canonical.sha384(Canonical.json(plain(t)));
    }

    /** A table as plain data: {@code columns} (name and type) and {@code rows}, numbers as their raw-bit hex. */
    public static Map<String, Object> plain(Table t) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> cols = new ArrayList<>();
        for (ColumnSchema c : t.schema().columns()) {
            cols.add(List.of(c.name(), c.type()));
        }
        m.put("columns", cols);
        List<Object> rows = new ArrayList<>();
        for (Object[] r : Tables.rows(t)) {
            List<Object> row = new ArrayList<>();
            for (Object v : r) {
                row.add(v instanceof Double d ? Canonical.bits(d) : v instanceof double[] a ? bitsOf(a) : v);
            }
            rows.add(row);
        }
        m.put("rows", rows);
        return m;
    }

    private static List<String> bitsOf(double[] a) {
        List<String> out = new ArrayList<>(a.length);
        for (double d : a) {
            out.add(Canonical.bits(d));
        }
        return out;
    }
}
