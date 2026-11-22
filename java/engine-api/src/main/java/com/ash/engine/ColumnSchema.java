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

/**
 * One column of a table. {@code type}: a Data Protocol type ({@code string}, {@code integer}, {@code number}, {@code decimal},
 * {@code boolean}, {@code date}, {@code timestamp}, {@code time}, {@code list}). {@code exact}: {@code bits}, {@code relative:1e-12} or
 * {@code text} (decimal).
 */
public record ColumnSchema(String name, String type, boolean nullable, boolean measure, String unit, String format, String exact) {

    /** A plain column: not a measure, no unit or format, compared by the type's own rule. */
    public static ColumnSchema of(String name, String type, boolean nullable) {
        return new ColumnSchema(name, type, nullable, false, null, null, null);
    }

    /** The carrier a batch or table holds this column in. */
    public Carrier carrier() {
        return Carrier.of(type);
    }

    /** The Java array a column of a type is held in. */
    public enum Carrier {
        TEXT, INTEGER, NUMBER, FLAG, LIST;

        /** The carrier of a Data Protocol type; IllegalArgumentException for a type that is not one. */
        public static Carrier of(String type) {
            return switch (type) {
                case "string", "decimal", "date", "timestamp", "time" -> TEXT;
                case "integer" -> INTEGER;
                case "number" -> NUMBER;
                case "boolean" -> FLAG;
                case "list" -> LIST;
                default -> throw new IllegalArgumentException("not a column type: " + type);
            };
        }
    }
}
