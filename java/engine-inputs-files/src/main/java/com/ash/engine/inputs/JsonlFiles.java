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
package com.ash.engine.inputs;

import com.ash.engine.Descriptor;
import com.ash.engine.text.StrictYaml;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * An engine's inputs from a folder of JSONL files, for its tests and its own command line (never inside a host, where data comes only
 * through the host's {@code Inputs}): {@code <folder>/<input>.jsonl} or {@code <input>.jsonl.gz}, one JSON object per line. An input
 * with no file is absent. A column map renames the file's columns to the contract's ({@code { DESK: TT_DESK }}: the contract's DESK is
 * the file's TT_DESK), as a host's engine file does.
 */
public final class JsonlFiles {

    private JsonlFiles() {
    }

    /** The rows of every declared input found in {@code folder} (a null list: no file). */
    public static Map<String, List<Map<String, Object>>> read(Descriptor d, Path folder, Map<String, Map<String, String>> columnMaps) throws IOException {
        Map<String, List<Map<String, Object>>> out = new TreeMap<>();
        for (String input : d.inputs().keySet()) {
            Path plain = folder.resolve(input + ".jsonl");
            Path gz = folder.resolve(input + ".jsonl.gz");
            Path f = Files.isRegularFile(plain) ? plain : Files.isRegularFile(gz) ? gz : null;
            out.put(input, f == null ? null : rows(f, columnMaps.getOrDefault(input, Map.of())));
        }
        return out;
    }

    /** The rows of one JSONL file (gzip when its name ends in {@code .gz}); {@code map}: contract column to file column. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rows(Path file, Map<String, String> map) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream raw = Files.newInputStream(file);
                InputStream in = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                if (line.isBlank()) {
                    continue;
                }
                Object v;
                try {
                    v = StrictYaml.read(line);
                } catch (IllegalArgumentException e) {
                    throw new IOException(file.getFileName() + " line " + n + ": not a JSON object: " + e.getMessage(), e);
                }
                if (!(v instanceof Map<?, ?>)) {
                    throw new IOException(file.getFileName() + " line " + n + ": not a JSON object");
                }
                Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) v);
                map.forEach((contract, column) -> {
                    if (row.containsKey(column)) {
                        row.put(contract, row.remove(column));
                    }
                });
                out.add(row);
            }
        }
        return out;
    }
}
