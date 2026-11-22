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

import java.io.IOException;
import java.io.OutputStream;

/** Capability {@code explain} (and {@code workbook}). Required when any output has a measure column. Implemented by the Session. */
public interface Explaining {

    /** The steps from input values to the cell, under the request's visibility. */
    Explanation explain(Request request, Cell cell, Control control) throws EngineException;

    /** Writes one .xlsx for the request's outputs: a values-only {@code Data} sheet, a {@code Main} sheet of formulas. The stream is the host's; do not close it. */
    void workbook(Request request, OutputStream out, Control control) throws EngineException, IOException;
}
