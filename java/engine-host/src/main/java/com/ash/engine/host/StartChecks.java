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
import com.ash.engine.Descriptors;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The start checks of section 9.4, for one engine of one JAR and its engine file: the JAR's form and versions, the descriptor, the pin,
 * the trust rule and the certificate, the file against the descriptor, the options, the isolation. Each failure blocks that engine only,
 * in words with the key; none stops the host or another engine.
 */
public final class StartChecks {

    /** {@code folder} (the default: writing to the lib folder is the act of trust) or {@code pinned}. */
    public enum Trust { FOLDER, PINNED }

    /** The outcome: what blocks the engine (empty: it may start) and the isolation it runs under. */
    public record Verdict(List<Problem> problems, String isolation, Optional<Certificate> certificate) {

        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private StartChecks() {
    }

    /** The checks for {@code r} of {@code jar} under {@code file}; {@code kitMajor}: the major of the kit whose certificates the host accepts. */
    public static Verdict check(EngineJar jar, EngineJar.Registered r, EngineFile file, Trust trust, String kitMajor) {
        return check(jar, r, file, trust, kitMajor, Licence.ALL);
    }

    /** As {@link #check(EngineJar, EngineJar.Registered, EngineFile, Trust, String)}, with the installation's licence ({@code ENG-1014}). */
    public static Verdict check(EngineJar jar, EngineJar.Registered r, EngineFile file, Trust trust, String kitMajor, Licence licence) {
        List<Problem> p = new ArrayList<>(r.problems());
        Descriptor d = r.descriptor();
        Optional<Certificate> cert = Certificate.beside(jar.path());
        if (d == null) {
            return new Verdict(p, null, cert);
        }
        licence.check(d, jar.path().getFileName().toString()).ifPresent(p::add);
        Descriptors.problems(d).forEach(x -> p.add(new Problem("ENG-1001", d.id() + ": " + x)));
        String file0 = jar.path().getFileName().toString();
        EngineFile.Pin pin = file.pin();
        if (pin != null && pin.version() != null && !pin.version().equals(d.version())) {
            p.add(new Problem("ENG-1011", d.id() + ": the engine file pins version " + pin.version() + "; " + file0 + " is " + d.version()));
        }
        if (pin != null && pin.sha384() != null && !pin.sha384().equals(jar.sha384())) {
            p.add(new Problem("ENG-1011", d.id() + ": the engine file pins a SHA-384 that " + file0 + " does not have"));
        }
        if (trust == Trust.PINNED) {
            if (pin == null || pin.version() == null || pin.sha384() == null) {
                p.add(new Problem("ENG-1012", d.id() + ": trust is pinned and the engine file has no engine.version and engine.sha384"));
            } else if (cert.isEmpty() || !cert.get().certifies(jar.sha384(), kitMajor)) {
                p.add(new Problem("ENG-1012", d.id() + ": trust is pinned and no certificate beside " + file0 + " names its hash and a kit " + kitMajor + ".x"));
            }
        }
        if (pin != null && "process".equals(pin.run()) && !d.has("process")) {
            p.add(new Problem("ENG-1010", d.id() + ": engine.run is process, but the descriptor does not list the capability process"));
        }
        p.addAll(file.check(d));
        boolean shared = cert.isPresent() && cert.get().certifies(jar.sha384(), kitMajor) && cert.get().allowsShared();
        String isolation = file.access().isolation();
        if ("shared".equals(isolation) && !shared) {
            p.add(new Problem("ENG-1010", d.id() + ": access.isolation is shared, but no certificate beside " + file0
                    + " lists the laws filter-equivalence and interleaving; run it per-viewer or certify the JAR"));
        }
        if (isolation == null) {
            isolation = shared ? "shared" : "per-viewer";
        }
        boolean allRequest = !d.inputs().isEmpty() && d.inputs().values().stream().allMatch(i -> "request".equals(i.mode()) || i.open());
        return new Verdict(List.copyOf(p), allRequest ? "per-viewer" : isolation, cert);
    }
}
