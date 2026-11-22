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
package com.ash.sample.engines;

import static org.assertj.core.api.Assertions.assertThat;

import com.ash.engine.host.EngineLib;
import com.ash.engine.host.Provided;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The drop-in story without Drishti (acceptance tests A3 to A5 on the reference discovery): a lib folder read at start; the JAR copied
 * in is found by ServiceLoader in its own loader; an engine without an engine file is "not configured"; with one, "running"; an
 * engine file without a JAR, "not installed"; a JAR built for a newer engine API is "blocked" with the one sentence; two JARs that
 * register one id are both blocked; nothing stops the others.
 */
class DropInIT {

    private static final Path JAR = Path.of(System.getProperty("engine.jar", "missing.jar"));

    @Test
    void anEmptyFolderStartsWithNoEngines(@TempDir Path lib) throws Exception {
        try (EngineLib l = EngineLib.scan(lib, getClass().getClassLoader(), Provided.of("1.1", null, "0.12"), Map.of("wmean", true))) {
            assertThat(l.entry("wmean").state()).isEqualTo(EngineLib.State.NOT_INSTALLED);
            assertThat(l.entry("wmean").words()).contains("no JAR");
        }
    }

    @Test
    void aCopiedJarIsFoundAndBoundByItsEngineFile(@TempDir Path lib) throws Exception {
        Files.copy(JAR, lib.resolve("engine-sample-1.0.0.jar"));
        try (EngineLib l = EngineLib.scan(lib, getClass().getClassLoader(), Provided.of("1.1", null, "0.12"), Map.of("wmean", true))) {
            assertThat(l.entry("wmean").state()).isEqualTo(EngineLib.State.RUNNING);
            assertThat(l.entry("group-totals").state()).isEqualTo(EngineLib.State.NOT_CONFIGURED);
            assertThat(l.jars()).singleElement().satisfies(j -> {
                assertThat(j.engines()).containsExactly("wmean 1.0.0", "group-totals 1.0.0");
                assertThat(j.sha384()).hasSize(96);
                assertThat(j.certificate()).isFalse();
            });
            assertThat(l.entry("wmean").engine().type().getClassLoader()).isNotSameAs(getClass().getClassLoader());
        }
    }

    @Test
    void twoJarsOfOneIdAreBothBlocked(@TempDir Path lib) throws Exception {
        Files.copy(JAR, lib.resolve("engine-sample-1.0.0.jar"));
        Files.copy(JAR, lib.resolve("engine-sample-copy.jar"));
        try (EngineLib l = EngineLib.scan(lib, getClass().getClassLoader(), Provided.of("1.1", null, "0.12"), Map.of("wmean", true))) {
            assertThat(l.entry("wmean").state()).isEqualTo(EngineLib.State.BLOCKED);
            assertThat(l.entry("wmean").code()).isEqualTo("ENG-1006");
            assertThat(l.entry("wmean").words()).contains("engine-sample-1.0.0.jar").contains("engine-sample-copy.jar");
        }
    }

    @Test
    void anApiTooNewIsRefusedWithBothVersionsAndWhatToDo(@TempDir Path lib) throws Exception {
        rewrite(JAR, lib.resolve("engine-sample-next.jar"), "wmean", "api: \"0.1\"", "api: \"0.9\"", null, null);
        try (EngineLib l = EngineLib.scan(lib, getClass().getClassLoader(), Provided.of("1.1", null, "0.12"), Map.of("wmean", true, "group-totals", true))) {
            EngineLib.Entry e = l.entry("wmean");
            assertThat(e.state()).isEqualTo(EngineLib.State.BLOCKED);
            assertThat(e.code()).isEqualTo("ENG-1002");
            assertThat(e.words()).isEqualTo("wmean 1.0.0 (engine-sample-next.jar) was built for engine API 0.9; this host provides 0.1. "
                    + "Install a host release that provides 0.9 or later, or the engine build for 0.1. Nothing else is affected.");
            assertThat(l.entry("group-totals").state()).isEqualTo(EngineLib.State.RUNNING);
        }
    }

    @Test
    void aMissingPlatformLibraryIsRefusedInWords(@TempDir Path lib) throws Exception {
        Files.copy(JAR, lib.resolve("engine-sample-1.0.0.jar"));
        try (EngineLib l = EngineLib.scan(lib, getClass().getClassLoader(), Provided.of(null, null, null), Map.of("wmean", true))) {
            assertThat(l.entry("wmean").state()).isEqualTo(EngineLib.State.BLOCKED);
            assertThat(l.entry("wmean").code()).isEqualTo("ENG-1005");
            assertThat(l.entry("wmean").words()).contains("needs drishti-tools 1.1; this host provides none");
        }
    }

    /** A copy of {@code from} whose descriptor of {@code id} has two texts replaced (a second pair may be null). */
    static void rewrite(Path from, Path to, String id, String a, String b, String c, String d) throws IOException {
        try (JarFile in = new JarFile(from.toFile()); JarOutputStream out = new JarOutputStream(Files.newOutputStream(to))) {
            Enumeration<JarEntry> en = in.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                out.putNextEntry(new JarEntry(e.getName()));
                try (InputStream s = in.getInputStream(e)) {
                    byte[] bytes = s.readAllBytes();
                    if (e.getName().equals("META-INF/drishti-engine/" + id + ".engine.yaml")) {
                        String t = new String(bytes, StandardCharsets.UTF_8).replace(a, b);
                        if (c != null) {
                            t = t.replace(c, d);
                        }
                        bytes = t.getBytes(StandardCharsets.UTF_8);
                    }
                    out.write(bytes);
                }
                out.closeEntry();
            }
        }
    }
}
