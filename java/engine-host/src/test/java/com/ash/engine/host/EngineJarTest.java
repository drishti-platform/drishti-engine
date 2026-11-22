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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A JAR is never a start failure: what is wrong with it is a finding in words. */
class EngineJarTest {

    @Test
    void aFileThatIsNotAJarIsAFinding(@TempDir Path dir) throws Exception {
        Path f = Files.writeString(dir.resolve("broken.jar"), "not a zip");
        try (EngineJar j = EngineJar.open(f, getClass().getClassLoader(), Provided.of("1.1", null, null))) {
            assertThat(j.engines()).isEmpty();
            assertThat(j.problems()).singleElement().satisfies(p -> assertThat(p.code()).isEqualTo("ENG-1007"));
        }
    }

    @Test
    void aDescriptorWithoutARegisteredClassAndABundledPlatformClassAreFindings(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("half.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(f))) {
            out.putNextEntry(new JarEntry("META-INF/drishti-engine/calc.engine.yaml"));
            out.write(DESCRIPTOR.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("com/ash/engine/Engine.class"));
            out.write(new byte[] {(byte) 0xCA, (byte) 0xFE});
            out.closeEntry();
        }
        try (EngineJar j = EngineJar.open(f, getClass().getClassLoader(), Provided.of("1.1", null, null))) {
            assertThat(j.bundledPlatformClasses()).containsExactly("com.ash.engine.Engine");
            assertThat(j.problems()).anySatisfy(p -> assertThat(p.words()).contains("has no registered class"));
        }
        try (EngineLib lib = EngineLib.scan(dir, getClass().getClassLoader(), Provided.of("1.1", null, null), Map.of())) {
            assertThat(lib.jars()).singleElement().satisfies(l -> assertThat(l.notes()).anyMatch(n -> n.contains("platform class")));
        }
    }

    @Test
    void theLoaderSharesOnlyThePlatformPackages(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("empty.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(f))) {
            out.putNextEntry(new JarEntry("x.txt"));
            out.closeEntry();
        }
        try (EngineJarLoader l = new EngineJarLoader(f, getClass().getClassLoader())) {
            assertThat(l.loadClass("com.ash.engine.Engine")).isSameAs(com.ash.engine.Engine.class);
            assertThat(l.loadClass("java.util.List")).isSameAs(java.util.List.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> l.loadClass("org.junit.jupiter.api.Test"))
                    .isInstanceOf(ClassNotFoundException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> l.loadClass("com.ash.engine.host.EngineLib"))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    static final String DESCRIPTOR = """
            engine: 1
            id: calc
            version: 1.0.0
            title: A calculator
            about: Adds two numbers.
            publisher: { id: org.example, name: Example }
            api: "0.1"
            inputs: {}
            outputs:
              sum:
                title: Sum
                about: a plus b
                shape: row
                columns:
                  - { name: SUM, type: number, nullable: false, label: Sum }
            """;
}
