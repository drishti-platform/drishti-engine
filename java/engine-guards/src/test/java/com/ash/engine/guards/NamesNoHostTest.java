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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Names no host (acceptance test A1): no main source or resource of any module, and no case, names Drishti's code
 * ({@code com.ash.drishti}), a Drishti setting ({@code drishti.<key>}), Spring or a servlet type. The kit's own list of host types it
 * refuses is the one exemption, by file.
 */
class NamesNoHostTest {

    private static final Pattern HOST = Pattern.compile("com[./]ash[./]drishti|org[./]springframework|jakarta[./]servlet|javax[./]servlet|\\bdrishti\\.[a-z]");
    private static final Set<String> EXEMPT = Set.of("java/engine-tck/src/main/java/com/ash/engine/tck/Delivery.java");

    @Test
    void noModuleNamesAHost() {
        List<String> out = new ArrayList<>();
        for (String m : List.of("engine-api", "engine-host", "engine-tck", "engine-sample")) {
            Path dir = Repo.JAVA.resolve(m);
            List<Path> files = new ArrayList<>(Repo.files(dir.resolve("src/main"), ".java", ".yaml", ".properties"));
            if (m.equals("engine-sample")) {
                files.addAll(Repo.files(dir.resolve("tck"), ".yaml"));
                files.addAll(Repo.files(dir.resolve("src/test"), ".java"));
            }
            for (Path p : files) {
                String rel = Repo.rel(p);
                if (!EXEMPT.contains(rel) && HOST.matcher(Repo.read(p)).find()) {
                    out.add(rel);
                }
            }
        }
        assertEquals(List.of(), out, "sources that name a host");
    }
}
