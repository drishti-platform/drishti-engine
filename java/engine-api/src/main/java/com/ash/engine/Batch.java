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

/** Rows as column arrays. The arrays belong to the host and are reused for the next batch: copy what is kept. */
public interface Batch {

    int rows();

    String[] texts(String column);

    long[] integers(String column);

    double[] numbers(String column);

    boolean[] flags(String column);

    boolean isNull(String column, int row);
}
