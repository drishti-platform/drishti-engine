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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A declared engine function (specification 19.7, EN-66): a public method of the engine's session class with parameters among
 * {@link RowSet}, {@link FloatVectors}, {@link DoubleVectors} and plain values. The host scans the registered classes for it at JAR load,
 * checks it against the JAR's function index, lists it, and keeps it off until it is enabled and mapped.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EngineFunction {

    /** Unique within the engine. */
    String name();

    /** The function's own version, checked against the engine file's. */
    String version();

    /** One sentence for the administrator, the API documents and an agent's tool list. */
    String description();

    /** True: called per shard, its partials merged by the method marked {@link Merge} of the same name and version. */
    boolean mergeable() default false;

    /** The merge of a mergeable function's partials. */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Merge {
        String name();

        String version();
    }
}
