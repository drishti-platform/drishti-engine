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

import java.util.Set;

/** The rows a session may read: only through here. A host applies the site's column map, row rules and masks before delivery. */
public interface Inputs {

    /** Exactly the descriptor's inputs. */
    Set<String> names();

    /** A declared input; an undeclared name is an IllegalArgumentException. */
    InputTable table(String name);

    /**
     * A table of an open input, by the name the request's document gives it, resolved in the host's catalogue as the caller. Not there
     * for this caller: {@link InputTable#present()} is false. A name the request's document does not hold is refused ({@code ENG-2001}).
     */
    InputTable table(String input, String table);
}
