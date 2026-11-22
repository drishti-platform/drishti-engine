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
package com.ash.engine.host;

import com.ash.engine.Descriptor;
import java.util.Optional;
import java.util.Set;

/**
 * The installation's licence as the start check reads it (owner direction 2026-11-22): an engine names its product id in its descriptor;
 * the licence grants product ids; an engine whose product is not granted does not start, with one sentence naming the missing product.
 * The licence file's form and signing are designed with module licensing (modules and engines share one licence file), so a host plugs
 * its own reader in here; this check sits beside the version check (EN-34).
 */
@FunctionalInterface
public interface Licence {

    /** Every product granted: a development host, the kit and the command line. */
    Licence ALL = product -> true;

    /** True when the licence grants {@code product}. */
    boolean grants(String product);

    /** A licence that grants exactly these products. */
    static Licence of(Set<String> products) {
        Set<String> granted = Set.copyOf(products);
        return granted::contains;
    }

    /** The refusal for an engine of {@code file} whose product is not granted ({@code ENG-1014}), or empty when it is. */
    default Optional<Problem> check(Descriptor d, String file) {
        if (grants(d.product())) {
            return Optional.empty();
        }
        return Optional.of(new Problem("ENG-1014", d.id() + " " + d.version() + " (" + file + ") belongs to the product " + d.product()
                + ", which this installation's licence does not grant. Install a licence that grants " + d.product()
                + ", or remove the JAR. Nothing else is affected."));
    }
}
