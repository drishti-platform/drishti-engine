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

import com.ash.engine.inputs.RowInputs;
import com.ash.engine.Cell;
import com.ash.engine.ColumnSchema;
import com.ash.engine.Descriptor;
import com.ash.engine.Explaining;
import com.ash.engine.Explanation;
import com.ash.engine.Request;
import com.ash.engine.Step;
import com.ash.engine.Table;
import com.ash.engine.host.HostControl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Section 8.3, checked without knowing the measure: every measure cell explained, the last step equal to the cell, every formula
 * recomputed by Rachana's evaluator from the step's own given values; the workbook valid, its defined names recalculated by a
 * spreadsheet engine the author did not write (Apache POI) and equal to the engine's cells, its cached values equal to the
 * recalculated ones, and the same bytes twice.
 */
public final class ExplainCheck {

    /** What the explain group counted, for the report and the certificate. */
    public static final class Counts {
        int cells;
        int steps;
        int opaque;
        int workbooks;
        int names;
        int namesEqual;

        public int cells() { return cells; }
        public int steps() { return steps; }
        public int opaque() { return opaque; }
        public int names() { return names; }
        public int namesEqual() { return namesEqual; }
    }

    private final KitHost host;
    private final Descriptor d;
    private final ElOracle el;
    private final Counts counts = new Counts();

    public ExplainCheck(KitHost host, ElOracle el) {
        this.host = host;
        this.d = host.descriptor();
        this.el = el;
    }

    public Counts counts() {
        return counts;
    }

    /** The explain law and the workbook check for one case whose plain run answered {@code base}. */
    public List<Result> run(Cases.Case c, Outcome base) {
        List<Result> out = new ArrayList<>();
        if (!base.answered() || base.answer().tables().isEmpty()) {
            return out;
        }
        boolean anyMeasure = base.answer().tables().stream().anyMatch(t -> t.schema().columns().stream().anyMatch(ColumnSchema::measure));
        if (!anyMeasure || !c.laws().runs("explain")) {
            return out;
        }
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            if (!c.changes().isEmpty() && o.apply(c.changes()) != null) {
                out.add(Result.fail("explain", c.name(), "explain", "the changes did not apply"));
                return out;
            }
            if (!(o.session() instanceof Explaining ex)) {
                out.add(Result.fail("explain", c.name(), "explain", "an output has a measure, but the session does not implement Explaining"));
                return out;
            }
            Request r = o.request(o.caseVisibility(), c.request().seed());
            out.add(cells(c, base, ex, r, o));
            out.add(workbook(c, base, ex, r, o));
        } catch (Exception e) {
            out.add(Result.fail("explain", c.name(), "explain", e.toString()));
        }
        return out;
    }

    private Result cells(Cases.Case c, Outcome base, Explaining ex, Request r, KitHost.Opened o) throws Exception {
        int n = 0;
        for (Table t : base.answer().tables()) {
            Descriptor.Output out = d.output(t.schema().name());
            for (ColumnSchema col : t.schema().columns()) {
                if (!col.measure()) {
                    continue;
                }
                for (int row = 0; row < t.rows(); row++) {
                    if (t.isNull(col.name(), row)) {
                        continue;
                    }
                    TreeMap<String, String> key = new TreeMap<>();
                    for (String k : out.key()) {
                        key.put(k, RowInputs.text(t.value(k, row)));
                    }
                    Cell cell = new Cell(t.schema().name(), col.name(), key);
                    HostControl ctl = o.control();
                    Explanation e = host.within(() -> ex.explain(r, cell, ctl));
                    String why = explanation(col, t.value(col.name(), row), e);
                    if (why != null) {
                        return Result.fail("explain", c.name(), "explain", t.schema().name() + "." + col.name() + " " + key + ": " + why);
                    }
                    n++;
                }
            }
        }
        counts.cells += n;
        return Result.ok("explain", c.name(), "explain", n + " cells");
    }

    private String explanation(ColumnSchema col, Object value, Explanation e) {
        if (e == null || e.steps().isEmpty()) {
            return "no steps";
        }
        Step last = e.steps().get(e.steps().size() - 1);
        String d1 = valueDiff(col, value, last.value());
        if (d1 != null) {
            return "the last step is " + last.value() + ", the cell " + value + " (" + d1 + ")";
        }
        Map<String, Object> earlier = new LinkedHashMap<>();
        for (Step s : e.steps()) {
            counts.steps++;
            if (s.opaque()) {
                counts.opaque++;
                if (s.why() == null || s.why().isBlank() || s.cell() == null || s.cell().isBlank()) {
                    return "the opaque step " + s.name() + " says why and names its workbook cell";
                }
            } else {
                if (s.formula() == null || s.formula().isBlank()) {
                    return "the step " + s.name() + " has no formula and is not opaque";
                }
                Map<String, Object> doc = new LinkedHashMap<>(s.given());
                doc.put("step", new LinkedHashMap<>(earlier));
                Object got;
                try {
                    got = el.eval(s.formula(), doc);
                } catch (RuntimeException x) {
                    return "the formula of " + s.name() + " (" + s.formula() + ") does not evaluate: " + x.getMessage();
                }
                String diff = elDiff(s.value(), got);
                if (diff != null) {
                    return "the formula of " + s.name() + " (" + s.formula() + ") gives " + got + ", the step says " + s.value() + " (" + diff + ")";
                }
            }
            earlier.put(s.name(), s.value());
        }
        return null;
    }

    private static String valueDiff(ColumnSchema col, Object cell, Object step) {
        if (cell instanceof Double x && step instanceof Number y) {
            return Compare.numbers(col.exact(), x, y.doubleValue());
        }
        if (cell instanceof Long x && step instanceof Number y) {
            return x == y.longValue() && y.doubleValue() == x ? null : "integers differ";
        }
        return String.valueOf(cell).equals(String.valueOf(step)) ? null : "differ";
    }

    private static String elDiff(Object want, Object got) {
        if (want instanceof Number w && got instanceof Number g) {
            double a = w.doubleValue();
            double b = g.doubleValue();
            return Math.abs(a - b) <= 1e-12 * Math.max(Math.abs(a), Math.abs(b)) ? null : "beyond relative 1e-12";
        }
        return String.valueOf(want).equals(String.valueOf(got)) ? null : "differ";
    }

    private Result workbook(Cases.Case c, Outcome base, Explaining ex, Request r, KitHost.Opened o) throws Exception {
        ByteArrayOutputStream b1 = new ByteArrayOutputStream();
        ByteArrayOutputStream b2 = new ByteArrayOutputStream();
        host.within(() -> {
            ex.workbook(r, b1, o.control());
            ex.workbook(r, b2, o.control());
            return null;
        });
        if (!Arrays.equals(b1.toByteArray(), b2.toByteArray())) {
            return Result.fail("explain", c.name(), "workbook", "two calls for the same request wrote different bytes");
        }
        counts.workbooks++;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(b1.toByteArray()))) {
            Sheet data = wb.getSheet("Data");
            Sheet main = wb.getSheet("Main");
            if (data == null || main == null) {
                return Result.fail("explain", c.name(), "workbook", "the workbook needs the sheets Data and Main");
            }
            for (Row row : data) {
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        return Result.fail("explain", c.name(), "workbook", "Data holds values only; " + cell.getAddress() + " is a formula");
                    }
                }
            }
            for (Row row : main) {
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA && !((XSSFCell) cell).getCTCell().isSetV()) {
                        return Result.fail("explain", c.name(), "workbook", "Main!" + cell.getAddress() + " has no cached value");
                    }
                }
            }
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            int names = 0;
            for (Table t : base.answer().tables()) {
                if (t.rows() == 0) {
                    continue;
                }
                for (ColumnSchema col : t.schema().columns()) {
                    if (!col.measure() || t.isNull(col.name(), 0)) {
                        continue;
                    }
                    String name = ("OUT_" + t.schema().name() + "_" + col.name()).toUpperCase().replace('-', '_');
                    Name n = wb.getName(name);
                    if (n == null) {
                        return Result.fail("explain", c.name(), "workbook", "no defined name " + name);
                    }
                    CellReference ref = new AreaReference(n.getRefersToFormula(), wb.getSpreadsheetVersion()).getFirstCell();
                    org.apache.poi.ss.usermodel.Cell cell = wb.getSheet(ref.getSheetName()).getRow(ref.getRow()).getCell(ref.getCol());
                    double cached = cell.getCellType() == CellType.FORMULA ? cell.getNumericCellValue() : cell.getNumericCellValue();
                    double recalculated = cell.getCellType() == CellType.FORMULA ? ev.evaluate(cell).getNumberValue() : cached;
                    double engine = ((Number) t.value(col.name(), 0)).doubleValue();
                    counts.names++;
                    names++;
                    if (!close(recalculated, engine, 1e-9)) {
                        return Result.fail("explain", c.name(), "workbook", name + " recalculates to " + recalculated + "; the engine's cell is " + engine);
                    }
                    if (!close(cached, recalculated, 1e-12)) {
                        return Result.fail("explain", c.name(), "workbook", name + " caches " + cached + " and recalculates to " + recalculated);
                    }
                    counts.namesEqual++;
                }
            }
            return Result.ok("explain", c.name(), "workbook", names + " defined names recalculated by Apache POI, all equal");
        }
    }

    private static boolean close(double a, double b, double rel) {
        return a == b || Math.abs(a - b) <= rel * Math.max(Math.abs(a), Math.abs(b));
    }
}
