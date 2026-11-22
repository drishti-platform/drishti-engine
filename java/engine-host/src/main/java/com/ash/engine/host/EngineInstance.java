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
import com.ash.engine.Engine;
import com.ash.engine.EngineContext;
import com.ash.engine.EngineException;
import com.ash.engine.EngineException.Kind;
import com.ash.engine.Inputs;
import com.ash.engine.Session;
import com.ash.engine.Snapshot;
import java.util.concurrent.Callable;

/**
 * One engine instance and its lifecycle (specification 7.1): NEW, OPEN, FAILED, CLOSED. Every call into the engine runs with the
 * engine's loader as the thread's context loader. A call in a state that does not allow it is {@code ENG-5020}.
 */
public final class EngineInstance implements AutoCloseable {

    /** The instance's state. */
    public enum State { NEW, OPEN, FAILED, CLOSED }

    private final Engine engine;
    private final ClassLoader loader;
    private final String id;
    private volatile State state = State.NEW;
    private volatile String failure;

    public EngineInstance(String id, Engine engine, ClassLoader loader) {
        this.id = id;
        this.engine = engine;
        this.loader = loader;
    }

    public State state() {
        return state;
    }

    /** Why open failed, in the engine's words (null when it did not). */
    public String failure() {
        return failure;
    }

    public Engine engine() {
        return engine;
    }

    /** Opens the instance once; a refusal leaves it FAILED with the engine's words. */
    public synchronized void open(EngineContext context) throws EngineException {
        if (state != State.NEW) {
            throw new EngineException("ENG-5020", Kind.REFUSED, id + " is " + state + "; open is called once, on a new instance");
        }
        try {
            within(() -> {
                engine.open(context);
                return null;
            });
            state = State.OPEN;
        } catch (EngineException e) {
            state = State.FAILED;
            failure = e.getMessage();
            throw e;
        } catch (Exception | LinkageError e) {
            state = State.FAILED;
            failure = "the engine failed to open";
            throw new EngineException("ENG-9000", Kind.FAILED, id + " failed to open", java.util.Map.of(), e);
        }
    }

    /** A session of the open instance. */
    public Session session(Snapshot snapshot, Inputs inputs, Control control) throws EngineException {
        if (state != State.OPEN) {
            throw new EngineException("ENG-5020", Kind.REFUSED, id + " is " + state + "; a session needs an open instance");
        }
        return within(() -> engine.session(snapshot, inputs, control));
    }

    /** Runs {@code call} with the engine's loader as the context loader; an EngineException passes, anything else is {@code ENG-9000}. */
    public <T> T within(Callable<T> call) throws EngineException {
        Thread t = Thread.currentThread();
        ClassLoader before = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            return call.call();
        } catch (EngineException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new EngineException("ENG-9000", Kind.FAILED, id + " failed", java.util.Map.of(), e);
        } finally {
            t.setContextClassLoader(before);
        }
    }

    /** Closes the instance (sessions are the caller's to close first); harmless twice. */
    @Override
    public synchronized void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        try {
            within(() -> {
                engine.close();
                return null;
            });
        } catch (EngineException e) {
            failure = "close failed";
        }
    }
}
