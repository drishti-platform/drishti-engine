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

import java.util.List;

/**
 * The platform packages (section 17.1): the engine API and the public packages of drishti-tools, Drishti Lake and Rachana. A host
 * provides them, shared and parent-first; an engine compiles against them and never carries them. The host's own packages
 * ({@code com.ash.engine.host}, {@code com.ash.engine.tck}) and every {@code internal} package are not handed over.
 */
public final class Platform {

    /** The platform package prefixes, in dotted form. */
    public static final List<String> PREFIXES = List.of("com.ash.engine.", "com.ash.tools.", "com.ash.lake.", "com.ash.rachana.");

    /** Packages under a platform prefix that a host keeps to itself. */
    public static final List<String> HOST_ONLY = List.of("com.ash.engine.host.", "com.ash.engine.tck.");

    private Platform() {
    }

    /** True when {@code className} (dotted) is under a platform prefix: such a class is never taken from an engine JAR. */
    public static boolean isPlatform(String className) {
        return PREFIXES.stream().anyMatch(className::startsWith);
    }

    /** True when a platform class is handed to an engine: under a prefix, not a host-only package, not {@code internal}. */
    public static boolean isShared(String className) {
        return isPlatform(className) && HOST_ONLY.stream().noneMatch(className::startsWith) && !className.contains(".internal.");
    }

    /** The family library a platform class belongs to: {@code api}, {@code tools}, {@code lake} or {@code rachana}; null for none. */
    public static String library(String className) {
        if (className.startsWith("com.ash.engine.")) {
            return "api";
        }
        if (className.startsWith("com.ash.tools.")) {
            return "tools";
        }
        if (className.startsWith("com.ash.lake.")) {
            return "lake";
        }
        return className.startsWith("com.ash.rachana.") ? "rachana" : null;
    }
}
