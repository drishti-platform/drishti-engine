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

/**
 * An engine's answer. {@code tables}: one per output answered, in the order asked. {@code COMPLETE}: missing is empty.
 * {@code NOT_FOUND}: tables is empty. Build one with {@link Answers}.
 */
public record Answer(Status status, List<Table> tables, List<Missing> missing, Stamp stamp, List<String> notes) {

    public Answer {
        tables = List.copyOf(tables == null ? List.of() : tables);
        missing = List.copyOf(missing == null ? List.of() : missing);
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    /** The table of {@code output}, or null when the answer has none. */
    public Table table(String output) {
        return tables.stream().filter(t -> t.schema().name().equals(output)).findFirst().orElse(null);
    }
}
