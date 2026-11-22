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

import com.ash.engine.Answer;
import com.ash.engine.Control;
import com.ash.engine.Descriptor;
import com.ash.engine.Engine;
import com.ash.engine.EngineContext;
import com.ash.engine.Inputs;
import com.ash.engine.Request;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SessionsTest {

    /** An engine whose sessions count themselves. */
    static final class Counting implements Engine {
        final AtomicInteger opened = new AtomicInteger();
        final AtomicInteger closed = new AtomicInteger();

        @Override public Descriptor descriptor() { return null; }
        @Override public void open(EngineContext context) { }
        @Override public Session session(Snapshot snapshot, Inputs inputs, Control control) {
            opened.incrementAndGet();
            return new Session() {
                @Override public Snapshot snapshot() { return snapshot; }
                @Override public long memoryBytes() { return 1; }
                @Override public Answer ask(Request request, Control c) { return null; }
                @Override public void close() { closed.incrementAndGet(); }
            };
        }
        @Override public void close() { }
    }

    private static Sessions.Key key(String date) {
        return new Sessions.Key(Snapshot.of(Map.of("date", date)), null);
    }

    @Test
    void oneSessionPerKeyKeptWhileHotAllowsAndNeverClosedWhileLeased() throws Exception {
        Counting c = new Counting();
        EngineInstance inst = new EngineInstance("count", c, getClass().getClassLoader());
        inst.open(null);
        try (Sessions s = new Sessions(inst, 2, k -> null)) {
            Sessions.Lease a = s.lease(key("2026-01-13"), HostControl.open());
            s.lease(key("2026-01-13"), HostControl.open()).close();
            assertThat(c.opened.get()).isEqualTo(1);
            s.lease(key("2026-01-14"), HostControl.open()).close();
            s.lease(key("2026-01-15"), HostControl.open()).close();
            assertThat(c.closed.get()).isEqualTo(1);                     // the 14th left; the 13th is leased
            assertThat(s.keys()).contains(key("2026-01-13"));
            a.close();
            s.stale(Snapshot.of(Map.of("date", "2026-01-15")));
            s.lease(key("2026-01-15"), HostControl.open()).close();
            assertThat(c.opened.get()).isEqualTo(4);
        }
        assertThat(c.closed.get()).isEqualTo(c.opened.get());
    }
}
