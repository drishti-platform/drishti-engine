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

import com.ash.engine.Control;
import com.ash.engine.EngineException;
import com.ash.engine.Inputs;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The sessions of one engine instance (specification 7.2): at most one per session key (the snapshot, and the visibility signature when
 * every input is read as the caller), opened once however many callers ask at the same time, kept while {@code hot} allows and closed
 * least recently used first, never while a call holds it. A stale session (an input moved) serves the calls in flight and is closed
 * when the last ends; the next lease opens a fresh one.
 */
public final class Sessions implements AutoCloseable {

    /** A session key: the snapshot, and the visibility signature for a per-viewer session (null for a shared one). */
    public record Key(Snapshot snapshot, String signature) {}

    /** One use of a session; closing it returns the session to the pool. */
    public interface Lease extends AutoCloseable {

        Session session();

        @Override
        void close();
    }

    private final EngineInstance instance;
    private final int hot;
    private final Function<Key, Inputs> inputs;
    private final Map<Key, Entry> open = new LinkedHashMap<>(16, 0.75f, true);
    private final List<Entry> retiring = new ArrayList<>();
    private boolean closed;

    private static final class Entry {
        final Key key;
        Session session;
        int inUse;
        boolean stale;
        boolean opening;

        Entry(Key key) {
            this.key = key;
        }
    }

    /** Sessions of {@code instance}; {@code hot}: how many are kept open; {@code inputs}: the host's inputs for a key. */
    public Sessions(EngineInstance instance, int hot, Function<Key, Inputs> inputs) {
        this.instance = instance;
        this.hot = Math.max(1, hot);
        this.inputs = inputs;
    }

    /** The session of {@code key}, opened when there is none; the caller closes the lease when its call ends. */
    public Lease lease(Key key, Control control) throws EngineException {
        Entry e;
        synchronized (this) {
            if (closed) {
                throw new EngineException("ENG-5020", EngineException.Kind.REFUSED, "the engine's sessions are closed");
            }
            e = open.get(key);
            if (e == null || e.stale) {
                if (e != null) {
                    retire(e);
                }
                e = new Entry(key);
                e.opening = true;
                open.put(key, e);
            }
            e.inUse++;
        }
        synchronized (e) {
            if (e.session == null) {
                try {
                    e.session = instance.session(key.snapshot(), inputs.apply(key), control);
                    synchronized (this) {
                        open.putIfAbsent(key, e);                   // a second caller that opened after the first failed
                    }
                } catch (EngineException | RuntimeException x) {
                    synchronized (this) {
                        e.inUse--;
                        open.remove(key, e);
                    }
                    throw x;
                } finally {
                    e.opening = false;
                }
            }
        }
        Entry held = e;
        evict();
        return new Lease() {
            private boolean done;

            @Override
            public Session session() {
                return held.session;
            }

            @Override
            public void close() {
                synchronized (Sessions.this) {
                    if (done) {
                        return;
                    }
                    done = true;
                    held.inUse--;
                }
                sweep();
                evict();
            }
        };
    }

    /** Marks every session of {@code snapshot} stale: calls in flight finish on it; the next lease opens a fresh one. */
    public synchronized void stale(Snapshot snapshot) {
        open.values().stream().filter(x -> x.key.snapshot().equals(snapshot)).forEach(x -> x.stale = true);
    }

    /** Bytes held by every open session. */
    public synchronized long memoryBytes() {
        long n = 0;
        for (Entry e : open.values()) {
            if (e.session != null) {
                n += e.session.memoryBytes();
            }
        }
        return n;
    }

    /** The keys of the sessions open now, least recently used first. */
    public synchronized List<Key> keys() {
        return List.copyOf(open.keySet());
    }

    private void retire(Entry e) {
        open.remove(e.key, e);
        retiring.add(e);
    }

    private void sweep() {
        List<Session> close = new ArrayList<>();
        synchronized (this) {
            retiring.removeIf(e -> {
                if (e.inUse == 0 && e.session != null) {
                    close.add(e.session);
                    return true;
                }
                return false;
            });
        }
        close.forEach(this::quietClose);
    }

    private void evict() {
        List<Session> close = new ArrayList<>();
        synchronized (this) {
            var it = open.values().iterator();
            int over = open.size() - hot;
            while (over > 0 && it.hasNext()) {
                Entry e = it.next();
                if (e.inUse == 0 && !e.opening && e.session != null) {
                    it.remove();
                    close.add(e.session);
                    over--;
                }
            }
        }
        close.forEach(this::quietClose);
    }

    private void quietClose(Session s) {
        try {
            instance.within(() -> {
                s.close();
                return null;
            });
        } catch (EngineException e) {
            // a session that fails to close is gone from the pool either way
        }
    }

    /** Closes every session; calls in flight should have been cancelled first. */
    @Override
    public void close() {
        List<Session> all = new ArrayList<>();
        synchronized (this) {
            closed = true;
            open.values().forEach(e -> {
                if (e.session != null) {
                    all.add(e.session);
                }
            });
            retiring.forEach(e -> {
                if (e.session != null) {
                    all.add(e.session);
                }
            });
            open.clear();
            retiring.clear();
        }
        all.forEach(this::quietClose);
    }
}
