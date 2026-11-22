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

import java.util.Map;

/**
 * A refusal or failure with a code ({@code ENG-nnnn} or the engine's own prefix) and words a person may read: what was asked, what is
 * allowed, what to do. Never a row's value, a path outside the work folder, a stack trace or a class name.
 */
public final class EngineException extends Exception {

    private static final long serialVersionUID = 1L;

    /** How a host shows it. */
    public enum Kind { REFUSED, NOT_PERMITTED, LIMIT, CANCELLED, TIMED_OUT, UNAVAILABLE, FAILED }

    private final String code;
    private final Kind kind;
    private final Map<String, String> detail;

    public EngineException(String code, Kind kind, String message) {
        this(code, kind, message, Map.of(), null);
    }

    public EngineException(String code, Kind kind, String message, Map<String, String> detail, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.kind = kind;
        this.detail = Map.copyOf(detail == null ? Map.of() : detail);
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    public Map<String, String> detail() {
        return detail;
    }

    /** True for LIMIT (after a wait), UNAVAILABLE, TIMED_OUT. */
    public boolean retry() {
        return kind == Kind.LIMIT || kind == Kind.UNAVAILABLE || kind == Kind.TIMED_OUT;
    }
}
