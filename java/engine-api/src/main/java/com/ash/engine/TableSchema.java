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

import java.util.List;

/** A table's schema: its name (the output's), columns in order, key columns, and the columns its rows are sorted by. */
public record TableSchema(String name, List<ColumnSchema> columns, List<String> key, List<String> order) {

    public TableSchema {
        columns = List.copyOf(columns);
        key = List.copyOf(key == null ? List.of() : key);
        order = List.copyOf(order == null || order.isEmpty() ? key : order);
    }

    /** The column named {@code name}, or null. */
    public ColumnSchema column(String name) {
        for (ColumnSchema c : columns) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** The position of {@code name}, or -1. */
    public int index(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
