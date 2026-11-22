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

import com.ash.engine.Descriptor;

/**
 * The engine file a host offers for an engine that is installed but not configured ({@code engine template <id>}): every option with its
 * default and words, the inputs, outputs and triggers as comments, made from the descriptor alone.
 */
public final class EngineTemplate {

    private EngineTemplate() {
    }

    /** The text of {@code config/engines/<id>.yaml} for {@code d}. */
    public static String of(Descriptor d) {
        StringBuilder b = new StringBuilder();
        b.append("# config/engines/").append(d.id()).append(".yaml: ").append(d.title()).append(" ").append(d.version()).append(" (")
                .append(d.publisher().name()).append(")\n# ").append(d.about().replace("\n", " ")).append("\n");
        b.append("drishti:\n  engines:\n    ").append(d.id()).append(":\n");
        b.append("      enabled: ${").append("DRISHTI_").append(d.id().toUpperCase().replace('-', '_')).append("_ENABLED:true}\n");
        b.append("      changed: { by: \"\", at: \"\", reason: \"\" }\n");
        b.append("      # engine: { version: ").append(d.version()).append(", sha384: \"<the JAR's SHA-384>\" }   # a pin; required under trust pinned\n");
        if (!d.inputs().isEmpty()) {
            b.append("      # inputs: bound by contract; name kinds or a table only where binding leaves it open\n");
            d.inputs().forEach((n, in) -> b.append("      #   ").append(n).append(": ")
                    .append(in.open() ? "open (tables named by each request)" : in.contract()).append(in.required() ? "" : " (optional)").append("\n"));
        }
        if (!d.options().isEmpty()) {
            b.append("      options:\n");
            d.options().forEach((n, t) -> b.append("        ").append(n).append(": ").append(t.defaultValue() == null ? "" : t.defaultValue())
                    .append("   # ").append(t.label() != null ? t.label() : "").append(t.help() != null ? ". " + t.help() : "").append("\n"));
        }
        b.append("      triggers:\n        - { on: request }\n");
        b.append("      outputs:\n");
        d.outputs().forEach((n, o) -> b.append("        ").append(n).append(": { serve: [ protocol ] }   # ").append(o.title()).append("\n"));
        b.append("      access: { opens: [ ], visibility: <a host rule> }   # isolation: per-viewer unless a certificate allows shared\n");
        b.append("      limits: { request-seconds: 30 }\n");
        return b.toString();
    }
}
