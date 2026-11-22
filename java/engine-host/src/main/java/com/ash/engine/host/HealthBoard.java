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

import com.ash.engine.EngineStats;
import com.ash.engine.Health;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * What the host does with the engines' health and stats (specification 7.6): polls every registered instance, keeps the last answer of
 * each (so a health page or a metrics scrape reads memory, never an engine), and derives the health of the packs that depend on an
 * engine: a DOWN or SUSPENDED engine makes each dependent pack DEGRADED. The host brings its own scheduler; this class starts no thread.
 */
public final class HealthBoard {

    /** One engine's last poll. */
    public record Entry(String engineId, Health health, EngineStats stats, Instant polledAt) {
    }

    /** A pack's derived health: UP, or DEGRADED with the engines that are not serving. */
    public record PackHealth(String packId, Health.State state, List<String> reasons) {
    }

    /** The whole board: every engine, every pack, and the worst state of all. */
    public record Report(List<Entry> engines, List<PackHealth> packs, Health.State overall, Instant at) {
    }

    private final Map<String, EngineInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, List<String>> packs = new ConcurrentHashMap<>();
    private final Map<String, Entry> last = new ConcurrentHashMap<>();

    /** Adds an engine instance to the board. */
    public void register(String engineId, EngineInstance instance) {
        instances.put(engineId, instance);
        last.put(engineId, poll(engineId, instance));
    }

    public void unregister(String engineId) {
        instances.remove(engineId);
        last.remove(engineId);
    }

    /** Declares that {@code packId} binds inputs or outputs of these engines (section 10). */
    public void pack(String packId, List<String> engineIds) {
        packs.put(packId, List.copyOf(engineIds));
    }

    /** Polls every instance now (cheap: each call reads what the engine already knows). */
    public void pollAll() {
        instances.forEach((id, i) -> last.put(id, poll(id, i)));
    }

    /** Polls on the host's scheduler; cancel the returned future to stop. */
    public ScheduledFuture<?> every(ScheduledExecutorService scheduler, long period, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(this::pollAll, period, period, unit);
    }

    /** The last polled state, no engine called. */
    public Report report() {
        List<Entry> engines = new ArrayList<>(new java.util.TreeMap<>(last).values());
        Map<String, Health.State> byId = new LinkedHashMap<>();
        engines.forEach(e -> byId.put(e.engineId(), e.health().state()));
        List<PackHealth> out = new ArrayList<>();
        new java.util.TreeMap<>(packs).forEach((pack, ids) -> {
            List<String> why = new ArrayList<>();
            for (String id : ids) {
                Health.State s = byId.get(id);
                if (s == null) {
                    why.add("engine " + id + " is not registered");
                } else if (!s.serving()) {
                    why.add("engine " + id + " is " + s);
                }
            }
            out.add(new PackHealth(pack, why.isEmpty() ? Health.State.UP : Health.State.DEGRADED, why));
        });
        Health.State worst = Health.State.UP;
        for (Entry e : engines) {
            worst = worse(worst, e.health().state());
        }
        for (PackHealth p : out) {
            worst = worse(worst, p.state());
        }
        return new Report(engines, out, worst, Instant.now());
    }

    private static Entry poll(String id, EngineInstance i) {
        return new Entry(id, i.health(), i.stats(), Instant.now());
    }

    private static Health.State worse(Health.State a, Health.State b) {
        return rank(b) > rank(a) ? b : a;
    }

    private static int rank(Health.State s) {
        return switch (s) {
            case UP -> 0;
            case SUSPENDED -> 1;
            case DEGRADED -> 2;
            case DOWN -> 3;
        };
    }
}
