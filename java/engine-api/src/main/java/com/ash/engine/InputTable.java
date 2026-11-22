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

/** One input's rows for the session's snapshot, delivered as column batches or vectors; optionally grouped at the source. */
public interface InputTable {

    String name();

    /** False: the table is not there for this snapshot. */
    boolean present();

    /** The declared columns that are there. */
    Set<String> columns();

    /** Changes when and only when the rows for this snapshot change. */
    String stamp();

    /** -1 when not known without reading. */
    long rows();

    /** Delivers batches on the calling thread. {@code where}: over scope columns; the host applies it exactly. */
    void scan(Scan scan, BatchSink sink) throws EngineException;

    /** One vector column with some text columns, a row at a time, in a stable order. */
    void vectors(VectorScan scan, VectorSink sink) throws EngineException;

    /** Push-down. true: the source can answer this grouped scan exactly; then {@link #groups} MAY be called. Cheap. */
    boolean offers(GroupScan scan);

    /** One batch stream per grouping set: the set's columns, then one column per aggregate, in order. Row rules are applied first. */
    void groups(GroupScan scan, BatchSink sink) throws EngineException;
}
