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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One line of a kit run: a group ({@code delivery}, {@code descriptor}, {@code scan}, {@code contracts}, {@code cases}, {@code laws},
 * {@code lifecycle}, {@code explain}, {@code gates}), the case (or empty), the law or check, whether it held, and words. {@code hash}: the
 * answer's hash where one was compared. No timing: two runs give the same lines.
 */
public record Result(String group, String subject, String check, boolean ok, String words, String hash) {

    static Result ok(String group, String subject, String check, String words) {
        return new Result(group, subject, check, true, words, null);
    }

    static Result fail(String group, String subject, String check, String words) {
        return new Result(group, subject, check, false, words, null);
    }

    static Result of(String group, String subject, String check, String problem, String hash) {
        return new Result(group, subject, check, problem == null, problem == null ? "" : problem, hash);
    }

    /** The same line with other words (null keeps these). */
    Result withWords(String w) {
        return w == null ? this : new Result(group, subject, check, ok, w, hash);
    }

    /** The line as plain data for {@code results.jsonl}. */
    public Map<String, Object> plain() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", group);
        m.put("subject", subject);
        m.put("check", check);
        m.put("ok", ok);
        m.put("words", words);
        if (hash != null) {
            m.put("answer", hash);
        }
        return m;
    }
}
