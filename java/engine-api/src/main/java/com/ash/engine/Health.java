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
package com.ash.engine;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An engine's answer to "are you well?" (specification 7.4). A value, built from what the engine already knows: it is never computed by
 * probing a source, a file or a peer.
 *
 * @param state     the verdict; consistent with the lifecycle state (see {@link State})
 * @param reasons   why, in words a person may read (empty for a plain UP); never row values
 * @param checkedAt the engine's clock (the context's) when it formed the verdict
 */
public record Health(State state, List<String> reasons, Instant checkedAt) {

    /** The verdicts, and the lifecycle states each may accompany. */
    public enum State {
        /** Serving normally. Only while {@link LifecycleState#RUNNING}. */
        UP,
        /** Serving, but impaired: a native library fell back to Java, a cache is thrashing, a supplier is slow. Only while RUNNING. */
        DEGRADED,
        /** Not serving: not started, failed, or shut down ({@code NEW}, {@code INITIALISED}, {@code FAILED}, {@code SHUTDOWN}). */
        DOWN,
        /** Deliberately not taking work: {@link LifecycleState#SUSPENDED}. */
        SUSPENDED;

        /** True when the instance answers requests (UP or DEGRADED). */
        public boolean serving() {
            return this == UP || this == DEGRADED;
        }
    }

    public Health {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(checkedAt, "checkedAt");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static Health up(Instant at) {
        return new Health(State.UP, List.of(), at);
    }

    public static Health degraded(Instant at, String... why) {
        return new Health(State.DEGRADED, List.of(why), at);
    }

    public static Health down(Instant at, String... why) {
        return new Health(State.DOWN, List.of(why), at);
    }

    public static Health suspended(Instant at, String... why) {
        return new Health(State.SUSPENDED, List.of(why), at);
    }

    /** The verdict that matches a lifecycle state when the engine has nothing to add: RUNNING is UP, SUSPENDED is SUSPENDED, the rest DOWN. */
    public static Health of(LifecycleState lifecycle, Instant at) {
        return switch (lifecycle) {
            case RUNNING -> up(at);
            case SUSPENDED -> suspended(at, "suspended");
            case NEW -> down(at, "not initialised");
            case INITIALISED -> down(at, "not started");
            case FAILED -> down(at, "failed");
            case SHUTDOWN -> down(at, "shut down");
        };
    }

    /** True when {@code state} is a verdict that may accompany {@code lifecycle} (the table of {@link State}). */
    public static boolean consistent(State state, LifecycleState lifecycle) {
        return switch (lifecycle) {
            case RUNNING -> state.serving();
            case SUSPENDED -> state == State.SUSPENDED;
            default -> state == State.DOWN;
        };
    }
}
