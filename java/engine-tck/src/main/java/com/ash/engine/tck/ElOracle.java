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

import java.util.Map;

/**
 * The reference evaluator of Rachana-EL as the kit uses it: a step's formula over its given values and earlier steps, and a grouped
 * scan's row expressions. The kit's own engine is {@link RachanaEl}; an author never supplies one.
 */
public interface ElOracle {

    /** The value of {@code expression} over {@code document} (plain maps, lists and scalars): a Double, Long, String, Boolean, List or null. */
    Object eval(String expression, Map<String, Object> document);

    /** The evaluator's name and version, for the report and the certificate. */
    String name();
}
