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

import com.ash.engine.EngineApi;
import com.ash.engine.host.Provided;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The kit's version and what it provides, as a host, to the JAR it certifies. */
public final class Kit {

    private static final Properties P = load();

    private Kit() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = Kit.class.getResourceAsStream("kit.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("the kit's own kit.properties cannot be read", e);
        }
        return p;
    }

    /** The kit's version ({@code 0.1.0-SNAPSHOT}). Its major is the specification's. */
    public static String version() {
        return P.getProperty("version", "0.0.0");
    }

    /** The engine API, and the drishti-tools and Rachana versions on the kit's class path; no Lake in this kit. */
    public static Provided provided() {
        return new Provided(EngineApi.VERSION, blank(P.getProperty("tools")), null, blank(P.getProperty("rachana")));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() || s.startsWith("${") ? null : s;
    }
}
