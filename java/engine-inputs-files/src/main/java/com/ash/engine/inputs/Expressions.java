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
package com.ash.engine.inputs;

import java.util.Map;

/**
 * The evaluator of Rachana-EL row expressions a grouped scan carries ({@code GroupScan.where}, {@code Aggregate.of}), given by the host.
 * Without one, {@link RowInputs} offers no grouped scan and the engine scans.
 */
@FunctionalInterface
public interface Expressions {

    /** The value of {@code expression} over one row (plain values): a Number, String, Boolean or null. */
    Object row(String expression, Map<String, Object> row);
}
