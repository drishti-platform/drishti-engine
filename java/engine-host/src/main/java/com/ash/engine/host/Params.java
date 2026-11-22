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
import com.ash.engine.Descriptor;
import com.ash.engine.EngineException;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.Table;
import com.ash.engine.TableSchema;
import com.ash.engine.Tables;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A host's normalisation of a request's parameters and an instance's options (section 3.2): defaults filled, types converted, unknown
 * names refused ({@code ENG-3001}), values outside what is allowed refused ({@code ENG-3002}), an output that does not take a parameter
 * refused ({@code ENG-3003}); options refused with {@code ENG-1013}. A {@code table} parameter becomes a {@link Table}.
 */
public final class Params {

    private Params() {
    }

    /** The normalised parameters of a request for {@code outputs} (each declared), sorted by name. */
    public static SortedMap<String, Object> request(Descriptor d, List<String> outputs, Map<String, Object> given) throws EngineException {
        if (outputs.isEmpty()) {
            throw new EngineException("ENG-3003", Kind.REFUSED, "no output was asked; " + d.id() + " answers " + d.outputs().keySet());
        }
        Set<String> accepted = new LinkedHashSet<>();
        for (String o : outputs) {
            Descriptor.Output out = d.output(o);
            if (out == null) {
                throw new EngineException("ENG-3003", Kind.REFUSED, d.id() + " has no output " + o + "; it answers " + d.outputs().keySet());
            }
            accepted.addAll(out.params());
        }
        SortedMap<String, Object> out = new TreeMap<>();
        for (Map.Entry<String, Object> e : (given == null ? Map.<String, Object>of() : given).entrySet()) {
            String name = e.getKey();
            Descriptor.Typed t = d.params().get(name);
            if (t == null) {
                throw new EngineException("ENG-3001", Kind.REFUSED, name + " is not a parameter of " + d.id() + "; its parameters are " + d.params().keySet());
            }
            if (!accepted.contains(name)) {
                throw new EngineException("ENG-3003", Kind.REFUSED, "the outputs " + outputs + " do not take the parameter " + name + "; they take " + accepted);
            }
            out.put(name, convert("parameter " + name, t, e.getValue(), d, "ENG-3002"));
        }
        for (String name : accepted) {
            Descriptor.Typed t = d.params().get(name);
            if (!out.containsKey(name)) {
                if (t.defaultValue() != null) {
                    out.put(name, convert("parameter " + name, t, t.defaultValue(), d, "ENG-3002"));
                } else if (t.required()) {
                    throw new EngineException("ENG-3002", Kind.REFUSED, "the parameter " + name + " is required");
                }
            }
        }
        return out;
    }

    /** The instance's options, defaults filled and converted; unknown or outside what is allowed: {@code ENG-1013}. */
    public static SortedMap<String, Object> options(Descriptor d, Map<String, Object> given) throws EngineException {
        SortedMap<String, Object> out = new TreeMap<>();
        for (Map.Entry<String, Object> e : (given == null ? Map.<String, Object>of() : given).entrySet()) {
            Descriptor.Typed t = d.options().get(e.getKey());
            if (t == null) {
                throw new EngineException("ENG-1013", Kind.REFUSED, e.getKey() + " is not an option of " + d.id() + "; its options are " + d.options().keySet());
            }
            out.put(e.getKey(), convert("option " + e.getKey(), t, e.getValue(), d, "ENG-1013"));
        }
        for (Map.Entry<String, Descriptor.Typed> e : d.options().entrySet()) {
            if (!out.containsKey(e.getKey())) {
                if (e.getValue().defaultValue() != null) {
                    out.put(e.getKey(), convert("option " + e.getKey(), e.getValue(), e.getValue().defaultValue(), d, "ENG-1013"));
                } else if (e.getValue().required()) {
                    throw new EngineException("ENG-1013", Kind.REFUSED, "the option " + e.getKey() + " is required");
                } else {
                    out.put(e.getKey(), null);
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Object convert(String what, Descriptor.Typed t, Object v, Descriptor d, String code) throws EngineException {
        if (v == null) {
            return null;
        }
        Object c;
        try {
            c = switch (t.type()) {
                case "text" -> text(v);
                case "int" -> integer(v);
                case "number" -> number(v);
                case "decimal" -> new BigDecimal(v.toString()).toPlainString();
                case "bool" -> bool(v);
                case "date" -> LocalDate.parse(v.toString()).toString();
                case "enum" -> text(v);
                case "list" -> {
                    if (!(v instanceof List<?> l)) {
                        throw new IllegalArgumentException("a list is expected");
                    }
                    Descriptor.Typed item = new Descriptor.Typed(t.items(), null, null, null, null, null, null, null, null, false, null, null, null, null, null, null, null);
                    List<Object> out = new ArrayList<>();
                    for (Object x : l) {
                        out.add(convert(what, item, x, d, code));
                    }
                    yield List.copyOf(out);
                }
                case "records" -> {
                    if (!(v instanceof List<?> l)) {
                        throw new IllegalArgumentException("a list of records is expected");
                    }
                    List<Object> out = new ArrayList<>();
                    for (Object x : l) {
                        if (!(x instanceof Map<?, ?> m)) {
                            throw new IllegalArgumentException("a record is a mapping");
                        }
                        Map<String, Object> r = new TreeMap<>();
                        for (Map.Entry<?, ?> f : m.entrySet()) {
                            Descriptor.Typed ft = t.fields().get(String.valueOf(f.getKey()));
                            if (ft == null) {
                                throw new IllegalArgumentException("a record has the field " + f.getKey() + "; the fields are " + t.fields().keySet());
                            }
                            r.put(String.valueOf(f.getKey()), convert(what, ft, f.getValue(), d, code));
                        }
                        out.add(r);
                    }
                    yield List.copyOf(out);
                }
                case "document" -> {
                    if (!(v instanceof Map<?, ?> || v instanceof List<?>)) {
                        throw new IllegalArgumentException("a document (a mapping or a list) is expected");
                    }
                    yield v;
                }
                case "table" -> table(what, t, v, d);
                default -> throw new IllegalArgumentException("an unknown type " + t.type());
            };
        } catch (EngineException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EngineException(code, Kind.REFUSED, "the " + what + " is not a " + t.type() + ": " + e.getMessage());
        }
        check(what, t, c, code);
        return c;
    }

    private static void check(String what, Descriptor.Typed t, Object c, String code) throws EngineException {
        if (c instanceof Number n && !(c instanceof Long && "decimal".equals(t.type()))) {
            if (t.min() != null && n.doubleValue() < t.min()) {
                throw new EngineException(code, Kind.REFUSED, "the " + what + " is " + c + "; it is at least " + plain(t.min()));
            }
            if (t.max() != null && n.doubleValue() > t.max()) {
                throw new EngineException(code, Kind.REFUSED, "the " + what + " is " + c + "; it is at most " + plain(t.max()));
            }
        }
        if ("enum".equals(t.type()) && !t.options().contains(c)) {
            throw new EngineException(code, Kind.REFUSED, "the " + what + " is " + c + "; it is one of " + t.options());
        }
        if (t.allowed() != null && !t.allowed().isEmpty()) {
            boolean ok = t.allowed().stream().anyMatch(a -> a != null && (a.equals(c) || a instanceof Number an && c instanceof Number cn
                    && an.doubleValue() == cn.doubleValue() || a.toString().equals(c.toString())));
            if (!ok) {
                throw new EngineException(code, Kind.REFUSED, "the " + what + " is " + c + "; it is one of " + t.allowed());
            }
        }
    }

    private static String plain(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    private static String text(Object v) {
        if (v instanceof Map<?, ?> || v instanceof List<?>) {
            throw new IllegalArgumentException("a text is expected");
        }
        return v.toString();
    }

    private static Long integer(Object v) {
        if (v instanceof Long || v instanceof Integer || v instanceof Short) {
            return ((Number) v).longValue();
        }
        if (v instanceof Double x && x == Math.rint(x) && Math.abs(x) < 9e15) {
            return (long) (double) x;
        }
        if (v instanceof String s) {
            return Long.parseLong(s.strip());
        }
        throw new IllegalArgumentException("a whole number is expected");
    }

    private static Double number(Object v) {
        if (v instanceof Number n) {
            double x = n.doubleValue();
            if (Double.isNaN(x) || Double.isInfinite(x)) {
                throw new IllegalArgumentException("a finite number is expected");
            }
            return x;
        }
        if (v instanceof String s) {
            return number(Double.parseDouble(s.strip()));
        }
        throw new IllegalArgumentException("a number is expected");
    }

    private static Boolean bool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if ("true".equals(v) || "false".equals(v)) {
            return Boolean.valueOf((String) v);
        }
        throw new IllegalArgumentException("true or false is expected");
    }

    /**
     * Rows of a {@code table} parameter as a {@link Table} named after the parameter: the columns of the input it overlays (in that
     * input's order) or, without {@code overlays}, of the first row; types from the values (whole numbers integer, other numbers number,
     * lists list, true and false boolean, else string). More rows than {@code max-rows}: {@code ENG-5004}.
     */
    static Table table(String what, Descriptor.Typed t, Object v, Descriptor d) throws EngineException {
        if (v instanceof Table already) {
            return already;
        }
        if (!(v instanceof List<?> rows)) {
            throw new IllegalArgumentException("rows (a list of mappings) are expected");
        }
        if (t.maxRows() != null && rows.size() > t.maxRows()) {
            throw new EngineException("ENG-5004", Kind.LIMIT, "the " + what + " has " + rows.size() + " rows; it takes at most " + t.maxRows());
        }
        List<String> cols = new ArrayList<>();
        if (t.overlays() != null && d.inputs().get(t.overlays()) != null) {
            cols.addAll(d.inputs().get(t.overlays()).columns());
        } else if (!rows.isEmpty() && rows.get(0) instanceof Map<?, ?> first) {
            first.keySet().forEach(k -> cols.add(String.valueOf(k)));
        }
        Map<String, String> types = new LinkedHashMap<>();
        for (String c : cols) {
            String type = "string";
            for (Object r : rows) {
                if (!(r instanceof Map<?, ?> m)) {
                    throw new IllegalArgumentException("a row is a mapping of column to value");
                }
                Object x = m.get(c);
                if (x instanceof Long || x instanceof Integer) {
                    type = "number".equals(type) ? "number" : "integer";
                } else if (x instanceof Double) {
                    type = "number";
                } else if (x instanceof List<?>) {
                    type = "list";
                } else if (x instanceof Boolean) {
                    type = "boolean";
                } else if (x != null) {
                    type = "string";
                    break;
                }
            }
            types.put(c, type);
        }
        for (Object r : rows) {
            for (Object k : ((Map<?, ?>) r).keySet()) {
                if (!types.containsKey(String.valueOf(k))) {
                    throw new IllegalArgumentException("a row has the column " + k + ", which " + (t.overlays() == null ? "the first row" : "the input " + t.overlays()) + " does not have");
                }
            }
        }
        List<ColumnSchema> schema = new ArrayList<>();
        types.forEach((c, ty) -> schema.add(ColumnSchema.of(c, ty, true)));
        Tables.Builder b = Tables.of(new TableSchema(what.replace("parameter ", ""), schema, List.of(), List.of()));
        for (Object r : rows) {
            Map<?, ?> m = (Map<?, ?>) r;
            Object[] row = new Object[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                Object x = m.get(cols.get(i));
                String ty = types.get(cols.get(i));
                row[i] = x == null ? null : switch (ty) {
                    case "number" -> ((Number) x).doubleValue();
                    case "integer" -> ((Number) x).longValue();
                    case "list" -> ((List<?>) x).stream().mapToDouble(e -> ((Number) e).doubleValue()).toArray();
                    case "boolean" -> x;
                    default -> x.toString();
                };
            }
            b.row(row);
        }
        return b.build();
    }
}
