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
package com.ash.engine.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * engine-api depends on the JDK only (EN-3): its POM names no dependency, and its classes import nothing outside {@code java.} and
 * {@code com.ash.engine}. engine-host adds engine-api and nothing else.
 */
class EngineApiHasNoDependencyTest {

    @Test
    void theApiPomNamesNoDependency() {
        String pom = Repo.read(Repo.JAVA.resolve("engine-api/pom.xml")).replaceAll("(?s)<!--.*?-->", "");
        assertTrue(!pom.contains("<dependency>"), "engine-api/pom.xml names a dependency");
    }

    @Test
    void theApiImportsTheJdkOnly() {
        assertEquals(List.of(), imports("engine-api", "java.", "com.ash.engine."));
    }

    @Test
    void theHostImportsTheJdkAndTheApiOnly() {
        assertEquals(List.of(), imports("engine-host", "java.", "com.ash.engine."));
        String pom = Repo.read(Repo.JAVA.resolve("engine-host/pom.xml")).replaceAll("(?s)<!--.*?-->", "");
        Matcher m = Pattern.compile("<artifactId>([^<]+)</artifactId></dependency>").matcher(pom);
        List<String> deps = new ArrayList<>();
        while (m.find()) {
            deps.add(m.group(1));
        }
        assertEquals(List.of("engine-api"), deps);
    }

    @Test
    void theFileInputsImportTheJdkAndTheApiOnly() {
        assertEquals(List.of(), imports("engine-inputs-files", "java.", "com.ash.engine."));
    }

    private static List<String> imports(String module, String... allowed) {
        List<String> out = new ArrayList<>();
        Pattern imp = Pattern.compile("(?m)^import (static )?([\\w.]+)");
        for (var p : Repo.files(Repo.JAVA.resolve(module).resolve("src/main"), ".java")) {
            Matcher m = imp.matcher(Repo.read(p));
            while (m.find()) {
                String name = m.group(2);
                boolean ok = false;
                for (String a : allowed) {
                    ok |= name.startsWith(a);
                }
                if (!ok) {
                    out.add(Repo.rel(p) + " imports " + name);
                }
            }
        }
        return out;
    }
}
