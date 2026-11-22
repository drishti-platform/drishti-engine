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

/** An input, or columns of it, that were not there; {@code outputs}: the outputs that could not be answered for it. */
public record Missing(String input, String contract, Snapshot snapshot, List<String> columns, List<String> outputs) {

    public Missing {
        columns = List.copyOf(columns == null ? List.of() : columns);
        outputs = List.copyOf(outputs == null ? List.of() : outputs);
        snapshot = snapshot == null ? Snapshot.NONE : snapshot;
    }

    /** {@code not found: risk/scenario-pv@1 for date 2026-01-13; missing columns: DESK}. */
    public String words() {
        StringBuilder b = new StringBuilder("not found: ").append(contract == null || contract.isEmpty() ? input : contract);
        if (!snapshot.dimensions().isEmpty()) {
            b.append(" for ");
            snapshot.dimensions().forEach((k, v) -> b.append(b.charAt(b.length() - 1) == ' ' ? "" : ", ").append(k).append(' ').append(v));
        }
        if (!columns.isEmpty()) {
            b.append("; missing columns: ").append(String.join(", ", columns));
        }
        return b.toString();
    }
}
