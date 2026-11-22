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

/** The rows of one input that changed since the session's stamp. {@code upserts}: whole rows. {@code stamp}: the input's new stamp. */
public record Changes(String input, Batch upserts, List<String> deletedKeys, String stamp) {

    public Changes {
        deletedKeys = List.copyOf(deletedKeys == null ? List.of() : deletedKeys);
    }
}
