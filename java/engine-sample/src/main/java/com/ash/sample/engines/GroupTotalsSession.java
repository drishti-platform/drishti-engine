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

import com.ash.engine.Aggregate;
import com.ash.engine.Answer;
import com.ash.engine.Answers;
import com.ash.engine.Cell;
import com.ash.engine.Control;
import com.ash.engine.EngineContext;
import com.ash.engine.EngineException;
import com.ash.engine.EngineMeter;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.Explaining;
import com.ash.engine.Explanation;
import com.ash.engine.Filter;
import com.ash.engine.GroupScan;
import com.ash.engine.InputTable;
import com.ash.engine.Inputs;
import com.ash.engine.Missing;
import com.ash.engine.Request;
import com.ash.engine.Scan;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import com.ash.engine.Steps;
import com.ash.engine.TableSchema;
import com.ash.engine.Tables;
import com.ash.tools.xlsx.Sheet;
import com.ash.tools.xlsx.Workbook;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A group-totals session: holds nothing but the inputs' handle; every ask reads the items as the caller. */
final class GroupTotalsSession implements Session, Explaining {

    static final List<String> COLUMNS = List.of("ID", "GROUP", "QTY", "AMOUNT");

    private final EngineContext context;
    private final TableSchema schema;
    private final Snapshot snapshot;
    private final Inputs inputs;
    private final EngineMeter meter;
    private final boolean fixedOrder;

    GroupTotalsSession(EngineContext context, EngineMeter meter, TableSchema schema, Snapshot snapshot, Inputs inputs, boolean fixedOrder) {
        this.meter = meter;
        meter.sessionOpened();
        this.context = context;
        this.schema = schema;
        this.snapshot = snapshot;
        this.inputs = inputs;
        this.fixedOrder = fixedOrder;
    }

    /** One item, as the explanation and the workbook show it. */
    record Item(String id, String group, long qty, double amount) {}

    /** One group's totals. */
    record Totals(long items, long qty, double amount) {}

    @Override
    public Snapshot snapshot() {
        return snapshot;
    }

    @Override
    public long memoryBytes() {
        return 0;
    }

    @Override
    public Answer ask(Request request, Control control) throws EngineException {
        return meter.measure(() -> compute(request, control), 0, 0);
    }

    private Answer compute(Request request, Control control) throws EngineException {
        InputTable t = inputs.table("items");
        Missing missing = missing(t, request);
        if (missing != null) {
            return Answers.notFound(context, this, request, List.of(missing));
        }
        Filter see = see(request);
        Map<String, Totals> totals = new TreeMap<>();
        GroupScan grouped = new GroupScan(List.of(List.of("GROUP")), List.of(new Aggregate("ITEMS", Aggregate.Fn.COUNT, null, null),
                new Aggregate("QTY", Aggregate.Fn.SUM, "QTY", null), new Aggregate("AMOUNT", Aggregate.Fn.SUM, "AMOUNT", null)), null, 4_096);
        if (!fixedOrder && see instanceof Filter.All && t.offers(grouped)) {
            t.groups(grouped, b -> {
                control.check();
                String[] g = b.texts("GROUP");
                long[] n = b.integers("ITEMS");
                long[] q = b.integers("QTY");
                double[] a = b.numbers("AMOUNT");
                for (int i = 0; i < b.rows(); i++) {
                    totals.put(g[i], new Totals(n[i], q[i], a[i]));
                }
            });
        } else {
            Map<String, List<Item>> byGroup = items(t, see, control);
            byGroup.forEach((g, list) -> {
                long q = 0;
                double a = 0;
                for (Item it : list) {
                    q += it.qty();
                    a += it.amount();
                }
                totals.put(g, new Totals(list.size(), q, a));
            });
        }
        Tables.Builder out = Tables.of(schema);
        totals.forEach((g, x) -> out.row(g, x.items(), x.qty(), x.amount()));
        return Answers.complete(context, this, request, List.of(out.build()));
    }

    private Missing missing(InputTable t, Request request) {
        if (!t.present()) {
            return new Missing("items", "sample/item@1", snapshot, List.of(), request.outputs());
        }
        List<String> lacking = COLUMNS.stream().filter(c -> !t.columns().contains(c)).toList();
        return lacking.isEmpty() ? null : new Missing("items", "sample/item@1", snapshot, lacking, request.outputs());
    }

    private static Filter see(Request request) throws EngineException {
        Filter f = request.visibility() == null ? null : request.visibility().rows().get("items");
        if (f == null) {
            throw new EngineException("ENG-4002", Kind.NOT_PERMITTED, "the request names no rows of items it may read");
        }
        return f;
    }

    /** The items the filter lets through, by group, each group in ID order (the fixed order of summation). */
    private static Map<String, List<Item>> items(InputTable t, Filter see, Control control) throws EngineException {
        Map<String, TreeMap<String, Item>> by = new TreeMap<>();
        t.scan(new Scan(COLUMNS, see, 4_096), b -> {
            control.check();
            String[] id = b.texts("ID");
            String[] g = b.texts("GROUP");
            long[] q = b.integers("QTY");
            double[] a = b.numbers("AMOUNT");
            for (int i = 0; i < b.rows(); i++) {
                if (id[i] == null || g[i] == null || b.isNull("QTY", i) || b.isNull("AMOUNT", i)) {
                    continue;
                }
                by.computeIfAbsent(g[i], k -> new TreeMap<>()).put(id[i], new Item(id[i], g[i], q[i], a[i]));
            }
        });
        Map<String, List<Item>> out = new TreeMap<>();
        by.forEach((g, m) -> out.put(g, new ArrayList<>(m.values())));
        return out;
    }

    @Override
    public Explanation explain(Request request, Cell cell, Control control) throws EngineException {
        InputTable t = inputs.table("items");
        String group = cell.key().get("GROUP");
        if (missing(t, request) != null || group == null || !"totals".equals(cell.output()) || !List.of("QTY", "AMOUNT").contains(cell.column())) {
            throw new EngineException("ENG-3005", Kind.REFUSED, "only QTY and AMOUNT of totals, for a group in the answer, are explained");
        }
        List<Item> list = items(t, see(request), control).get(group);
        if (list == null) {
            throw new EngineException("ENG-3005", Kind.REFUSED, "the group " + group + " is not in this request's answer");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        long q = 0;
        double a = 0;
        for (Item it : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", it.id());
            m.put("QTY", it.qty());
            m.put("AMOUNT", it.amount());
            rows.add(m);
            q += it.qty();
            a += it.amount();
        }
        Object value = "QTY".equals(cell.column()) ? (Object) q : (Object) a;
        return new Explanation(cell, value, List.of(Steps.of("total", "Sum of " + cell.column() + " over the group's " + rows.size() + " items, in ID order",
                "sum($.rows, '" + cell.column() + "')", Map.of("rows", rows), value)));
    }

    @Override
    public void workbook(Request request, OutputStream out, Control control) throws EngineException, IOException {
        Answer answer = ask(request, new QuietControl(control));
        InputTable t = inputs.table("items");
        Map<String, List<Item>> items = missing(t, request) == null ? items(t, see(request), control) : Map.of();
        try (Workbook wb = Workbook.to(out)) {
            Sheet data = wb.sheet("Data");
            data.row(1).text(0, "ID", "header").text(1, "GROUP", "header").text(2, "QTY", "header").text(3, "AMOUNT", "header");
            int n = 0;
            for (List<Item> list : items.values()) {
                for (Item it : list) {
                    data.next().text(0, it.id(), "normal").text(1, it.group(), "normal").number(2, it.qty(), "int").number(3, it.amount(), "decimal");
                    n++;
                }
            }
            int last = Math.max(2, n + 1);
            Sheet main = wb.sheet("Main");
            main.row(1).text(0, "GROUP", "header").text(1, "ITEMS", "header").text(2, "QTY", "header").text(3, "AMOUNT", "header");
            var table = answer.tables().isEmpty() ? null : answer.tables().get(0);
            for (int i = 0; table != null && i < table.rows(); i++) {
                int r = i + 2;
                String key = "A" + r;
                main.row(r).text(0, table.texts("GROUP")[i], "normal").number(1, table.integers("ITEMS")[i], "int")
                        .formula(2, "SUMIF(Data!$B$2:$B$" + last + "," + key + ",Data!$C$2:$C$" + last + ")", (double) table.integers("QTY")[i], "int")
                        .formula(3, "SUMIF(Data!$B$2:$B$" + last + "," + key + ",Data!$D$2:$D$" + last + ")", table.numbers("AMOUNT")[i], "decimal");
            }
            if (table != null && table.rows() > 0) {
                wb.name("OUT_TOTALS_QTY", main.name(), "$C$2");
                wb.name("OUT_TOTALS_AMOUNT", main.name(), "$D$2");
            }
        }
    }

    @Override
    public void close() {
        meter.sessionClosed();
        // nothing is held between asks
    }
}
