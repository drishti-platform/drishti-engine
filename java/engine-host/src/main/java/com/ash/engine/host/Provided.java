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

import com.ash.engine.EngineApi;

/**
 * What a host provides: the engine API and, for each family library it ships, its version ({@code null}: not provided). Each host release
 * states it in one line: "provides engine API 1.4, tools 1.2, Lake 0.9, Rachana 1.0".
 */
public record Provided(String api, String tools, String lake, String rachana) {

    /** This engine-api's version and the given family versions. */
    public static Provided of(String tools, String lake, String rachana) {
        return new Provided(EngineApi.VERSION, tools, lake, rachana);
    }

    /** The version provided for a library ({@code api}, {@code tools}, {@code lake}, {@code rachana}). */
    public String of(String library) {
        return switch (library) {
            case "api" -> api;
            case "tools" -> tools;
            case "lake" -> lake;
            case "rachana" -> rachana;
            default -> throw new IllegalArgumentException("not a family library: " + library);
        };
    }

    /** "provides engine API 0.1, tools 1.1, Lake 0.9, Rachana 0.12". */
    public String line() {
        StringBuilder b = new StringBuilder("provides engine API ").append(api);
        if (tools != null) {
            b.append(", tools ").append(tools);
        }
        if (lake != null) {
            b.append(", Lake ").append(lake);
        }
        if (rachana != null) {
            b.append(", Rachana ").append(rachana);
        }
        return b.toString();
    }
}
