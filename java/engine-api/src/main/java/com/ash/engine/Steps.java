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

import java.util.Map;
import java.util.TreeMap;

/** Explanation steps in their one form. */
public final class Steps {

    private Steps() {
    }

    /** A step with a Rachana-EL formula over {@code given} (and earlier steps as {@code step.<name>}) and the engine's own value. */
    public static Step of(String name, String label, String formula, Map<String, ?> given, Object value) {
        return new Step(name, label, formula, new TreeMap<String, Object>(given), value, false, null, null);
    }

    /** A step not expressible in EL: why, and the workbook cell that holds it. Counted by the kit; an engine SHOULD have none. */
    public static Step opaque(String name, String label, String why, String workbookCell, Object value) {
        return new Step(name, label, null, new TreeMap<>(), value, true, why, workbookCell);
    }
}
