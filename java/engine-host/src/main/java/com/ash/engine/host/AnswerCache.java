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

import com.ash.engine.Answer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Answers kept by their stamp (specification 13 rule 6): the stamp holds the engine version, descriptor, options, input stamps,
 * request and seed, so a kept answer is served only to the same question over the same data. Least recently used leave first. An
 * interim answer is never kept.
 */
public final class AnswerCache {

    private final int entries;
    private final LinkedHashMap<String, Answer> map;

    public AnswerCache(int entries) {
        this.entries = Math.max(1, entries);
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Answer> e) {
                return size() > AnswerCache.this.entries;
            }
        };
    }

    /** The answer kept for {@code stampText}, or null. */
    public synchronized Answer get(String stampText) {
        return map.get(stampText);
    }

    /** Keeps a final answer under its own stamp. */
    public synchronized void put(Answer a) {
        if (a != null && a.stamp() != null && a.status() != com.ash.engine.Status.INTERIM) {
            map.put(a.stamp().text(), a);
        }
    }

    public synchronized int size() {
        return map.size();
    }
}
