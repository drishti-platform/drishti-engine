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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An engine's declaration ({@code META-INF/drishti-engine/<id>.engine.yaml}): the single source for binding, checks, generation and the
 * kit. {@code product}: the product id a host's licence must grant for the engine to start. Read with {@link Descriptors}. {@code raw} is the document as read (plain maps, lists and scalars), for hashing and for keys a
 * host reads as data ({@code ui}).
 */
public record Descriptor(int format, String id, String version, String title, String about, Publisher publisher, String product, String api,
        Map<String, String> platform, String worker, String codes, String licenceFeature, List<Dimension> snapshot,
        Map<String, Input> inputs, Map<String, Typed> options, Map<String, Typed> params, Map<String, Output> outputs,
        Set<String> capabilities, Partition partition, Map<String, Port> uses, Limits limits, Map<String, String> aliases,
        Map<String, Object> raw) {

    /** Who publishes the engine. */
    public record Publisher(String id, String name) {}

    /** One snapshot dimension: {@code date}, {@code text} or {@code int}. */
    public record Dimension(String name, String type, String label) {}

    /** One input (section 6.2.1 of the specification). {@code mode}: {@code session} or {@code request}. {@code changes}: {@code none} or {@code rows}. */
    public record Input(String contract, String about, boolean open, boolean required, String mode, List<String> key,
            List<String> columns, String vector, List<String> scopeColumns, String changes, List<String> orderedBy, List<String> per) {}

    /** An option or parameter declaration (section 3.2). Absent keys are null. */
    public record Typed(String type, Map<String, Object> schema, String contract, Integer maxRows, String overlays, String label,
            String help, String unit, Object defaultValue, boolean required, Double min, Double max, List<Object> allowed,
            List<String> options, String items, Map<String, Typed> fields, String forces) {

        /** True when this bool option forces the exact path ({@code forces: exact-path}, specification 8.1 rules 4 and 6). */
        public boolean forcesExactPath() {
            return "exact-path".equals(forces);
        }

        /** True when it has a {@code label} or {@code help}. */
        public boolean hasWords() {
            return label != null && !label.isBlank() || help != null && !help.isBlank();
        }
    }

    /** One output column. */
    public record Column(String name, String type, boolean nullable, String label, String unit, String format, Integer scale,
            boolean measure, String exact, String passes) {

        /** The column as a table column: {@code exact} defaults to {@code bits} for numbers and {@code text} for decimals. */
        public ColumnSchema schema() {
            String e = exact != null ? exact : "number".equals(type) || "list".equals(type) ? "bits" : "decimal".equals(type) ? "text" : null;
            return new ColumnSchema(name, type, nullable, measure, unit, format, e);
        }
    }

    /** One output (section 6.2.2). {@code shape}: {@code table}, {@code row} or {@code series}. */
    public record Output(String title, String about, String shape, List<String> params, List<String> needs, List<String> key,
            List<String> order, String axis, List<Column> columns, boolean paged, boolean stochastic, String contract) {

        /** The table schema an answer of this output, named {@code name}, has. */
        public TableSchema schema(String name) {
            List<ColumnSchema> cols = new ArrayList<>();
            columns.forEach(c -> cols.add(c.schema()));
            return new TableSchema(name, cols, key, order.isEmpty() ? key : order);
        }

        /** The measure columns, in order. */
        public List<Column> measures() {
            return columns.stream().filter(Column::measure).toList();
        }
    }

    /** The column whose values a shard is made of. */
    public record Partition(String input, String unit) {}

    /** A supplier port: another engine's output this one may ask. */
    public record Port(String contract, String about) {}

    /** Declared memory (fixed and per input row) and the cancel latency (default 1 s). */
    public record Limits(long fixedBytes, Map<String, Long> perRowBytes, Duration cancelLatency) {}

    /** The option that forces the exact path, or null when the engine declares none. */
    public String exactPathOption() {
        return options.entrySet().stream().filter(e -> e.getValue().forcesExactPath()).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /** True when the descriptor lists the capability. */
    public boolean has(String capability) {
        return capabilities.contains(capability);
    }

    /** The output named {@code name}, or null. */
    public Output output(String name) {
        return outputs.get(name);
    }

    /** The table schema of output {@code name}; IllegalArgumentException when there is none. */
    public TableSchema schema(String name) {
        Output o = outputs.get(name);
        if (o == null) {
            throw new IllegalArgumentException(id + " has no output " + name);
        }
        return o.schema(name);
    }

    /** The inputs an output cannot be answered without: its {@code needs}, else every required input. */
    public List<String> needs(String output) {
        Output o = outputs.get(output);
        if (o != null && !o.needs().isEmpty()) {
            return o.needs();
        }
        return inputs.entrySet().stream().filter(e -> e.getValue().required()).map(Map.Entry::getKey).sorted().toList();
    }
}
