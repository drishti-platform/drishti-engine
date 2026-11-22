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

/**
 * The one compatibility rule (section 17.5): the same major, and the provided minor at least the one built for. Below 1.0 the minor is
 * compared as the major and the patch as the minor.
 */
public final class Versions {

    private Versions() {
    }

    /** True when a host providing {@code provided} ({@code 1.4} or {@code 1.4.2}) runs a JAR built for {@code builtFor} ({@code 1.2}). */
    public static boolean compatible(String provided, String builtFor) {
        int[] p = parse(provided);
        int[] b = parse(builtFor);
        if (p[0] == 0 || b[0] == 0) {
            return p[0] == b[0] && p[1] == b[1] && p[2] >= b[2];
        }
        return p[0] == b[0] && p[1] >= b[1];
    }

    /** {@code major.minor} of a version text. */
    public static String majorMinor(String version) {
        int[] v = parse(version);
        return v[0] + "." + v[1];
    }

    static int[] parse(String v) {
        String[] parts = v.split("-", 2)[0].split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            out[i] = Integer.parseInt(parts[i]);
        }
        return out;
    }
}
