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

import com.ash.engine.Audit;
import com.ash.engine.Descriptor;
import com.ash.engine.EngineContext;
import com.ash.engine.Log;
import com.ash.engine.MemoryBudget;
import com.ash.engine.Options;
import com.ash.engine.Request;
import com.ash.engine.Snapshot;
import com.ash.engine.Stamp;
import com.ash.engine.Supplier;
import com.ash.engine.text.Canonical;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * An engine instance's context as a host builds it: options, a work folder, the business clock, the bounded executor, the budget, audit
 * and log lines kept in order, supplier ports, the peer at another snapshot, and the stamp. The reference host of the kit and of the
 * command line; Drishti builds the same from its configuration.
 */
public final class HostContext implements EngineContext {

    private final Descriptor descriptor;
    private final String descriptorHash;
    private final SortedMap<String, Object> options;
    private final Path workDir;
    private final Clock clock;
    private final ExecutorService executor;
    private final Budget budget;
    private final Map<String, Supplier> suppliers;
    private final Function<Snapshot, Optional<Supplier>> peer;
    private final Function<Snapshot, SortedMap<String, String>> inputStamps;
    private final List<String> audit = new CopyOnWriteArrayList<>();
    private final List<String> log = new CopyOnWriteArrayList<>();

    /**
     * @param descriptor the engine's descriptor; its canonical form is hashed into every stamp
     * @param options normalised ({@link Params#options})
     * @param inputStamps the stamps of the inputs the host delivers for a snapshot (input name, or {@code input:table}, to stamp)
     */
    public HostContext(Descriptor descriptor, SortedMap<String, Object> options, Path workDir, Clock clock, ExecutorService executor,
            Budget budget, Map<String, Supplier> suppliers, Function<Snapshot, Optional<Supplier>> peer,
            Function<Snapshot, SortedMap<String, String>> inputStamps) {
        this.descriptor = descriptor;
        this.descriptorHash = Canonical.sha384(Canonical.json(descriptor.raw()));
        this.options = Collections.unmodifiableSortedMap(new TreeMap<>(options));
        this.workDir = workDir;
        this.clock = clock;
        this.executor = executor;
        this.budget = budget;
        this.suppliers = Map.copyOf(suppliers);
        this.peer = peer;
        this.inputStamps = inputStamps;
    }

    @Override
    public Options options() {
        return () -> options;
    }

    @Override
    public Path workDir() {
        return workDir;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public ExecutorService executor() {
        return executor;
    }

    @Override
    public MemoryBudget memory() {
        return budget;
    }

    /** The budget as the host sees it (used, peak, refusals). */
    public Budget budget() {
        return budget;
    }

    @Override
    public Audit audit() {
        return (id, words) -> audit.add(id + " " + words);
    }

    @Override
    public Log log() {
        return new Log() {
            @Override public void debug(String words) { log.add("debug " + words); }
            @Override public void info(String words) { log.add("info " + words); }
            @Override public void warn(String words) { log.add("warn " + words); }
        };
    }

    @Override
    public Optional<Supplier> supplier(String port) {
        return Optional.ofNullable(suppliers.get(port));
    }

    @Override
    public Optional<Supplier> peer(Snapshot other) {
        return peer == null ? Optional.empty() : peer.apply(other);
    }

    @Override
    public Stamp stamp(Snapshot snapshot, Request request) {
        return new Stamp(descriptor.id(), descriptor.version(), descriptorHash, Stamping.options(options), inputStamps.apply(snapshot),
                Stamping.request(snapshot, request), request.seed());
    }

    /** The audit notes the engine wrote, in order. */
    public List<String> auditLines() {
        return List.copyOf(audit);
    }

    /** The log lines the engine wrote, in order. */
    public List<String> logLines() {
        return List.copyOf(log);
    }

    public String descriptorHash() {
        return descriptorHash;
    }
}
