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

import java.util.ArrayList;
import java.util.List;

/** Answers in their one form: the status follows from the tables and the missing inputs; the stamp from {@link Stamps}. */
public final class Answers {

    private Answers() {
    }

    /** Every output asked, answered. */
    public static Answer complete(EngineContext context, Session session, Request request, List<Table> tables) {
        return new Answer(Status.COMPLETE, tables, List.of(), Stamps.of(context, session, request), List.of());
    }

    /** No output answerable: every missing input named. */
    public static Answer notFound(EngineContext context, Session session, Request request, List<Missing> missing) {
        return new Answer(Status.NOT_FOUND, List.of(), missing, Stamps.of(context, session, request), List.of());
    }

    /** Some outputs answered, the others missing: COMPLETE when nothing is missing, NOT_FOUND when no table is given. */
    public static Answer of(EngineContext context, Session session, Request request, List<Table> tables, List<Missing> missing) {
        Status s = missing.isEmpty() ? Status.COMPLETE : tables.isEmpty() ? Status.NOT_FOUND : Status.PARTIAL;
        return new Answer(s, tables, missing, Stamps.of(context, session, request), List.of());
    }

    /** A first, approximate answer for {@link Control#interim}: status INTERIM and a note that says how it was estimated. */
    public static Answer interim(EngineContext context, Session session, Request request, List<Table> tables, String how) {
        return new Answer(Status.INTERIM, tables, List.of(), Stamps.of(context, session, request), List.of(how));
    }

    /** The same answer with notes added. */
    public static Answer withNotes(Answer a, List<String> notes) {
        List<String> n = new ArrayList<>(a.notes());
        n.addAll(notes);
        return new Answer(a.status(), a.tables(), a.missing(), a.stamp(), n);
    }
}
