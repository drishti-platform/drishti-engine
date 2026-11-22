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

import com.ash.engine.text.StrictYaml;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A kit certificate ({@code <jar name>.certificate.json} beside the JAR, specification 12.6): evidence, not a condition of loading. A host
 * shows it, or its absence, for every JAR, and requires it only where its trust rule says so.
 */
public record Certificate(String engine, String version, String jarSha384, String descriptorSha384, String api, String tck, List<String> lawsPassed) {

    /** The certificate beside {@code jar}, when there is one that can be read. */
    @SuppressWarnings("unchecked")
    public static Optional<Certificate> beside(Path jar) {
        Path f = jar.resolveSibling(jar.getFileName() + ".certificate.json");
        if (!Files.isRegularFile(f)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> m = StrictYaml.readMap(Files.readString(f, StandardCharsets.UTF_8).strip());
            Object laws = m.get("laws-passed");
            return Optional.of(new Certificate(str(m.get("engine")), str(m.get("version")), str(m.get("jar-sha384")), str(m.get("descriptor-sha384")),
                    str(m.get("api")), str(m.get("tck")), laws instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of()));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** True when it certifies this JAR (its hash) with a kit of an accepted major. */
    public boolean certifies(String sha384, String acceptedKitMajor) {
        return sha384 != null && sha384.equals(jarSha384) && tck != null && tck.split("\\.")[0].equals(acceptedKitMajor);
    }

    /** True when it lists both laws that allow a {@code shared} session (filter-equivalence and interleaving). */
    public boolean allowsShared() {
        return lawsPassed.contains("filter-equivalence") && lawsPassed.contains("interleaving");
    }
}
