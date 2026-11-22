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

import com.ash.engine.EngineException;
import com.ash.engine.RequestClass;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Admission by request class (specification 3.6): at most so many requests of a class at once; more is {@code ENG-5001} in words, never a queue without end. */
public final class Admission {

    private final Map<RequestClass, Semaphore> slots = new EnumMap<>(RequestClass.class);
    private final Map<RequestClass, Integer> limits = new EnumMap<>(RequestClass.class);

    /** Limits by class ({@code interactive}, {@code event}, {@code batch}); a class not named is unlimited. */
    public Admission(Map<String, Integer> concurrent) {
        concurrent.forEach((k, v) -> {
            RequestClass c = RequestClass.valueOf(k.toUpperCase());
            slots.put(c, new Semaphore(v));
            limits.put(c, v);
        });
    }

    /** A held place; closing it frees the place. */
    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    /** One permit; the caller closes it when the request ends. */
    public Permit admit(RequestClass c, String engine) throws EngineException {
        Semaphore s = slots.get(c);
        if (s == null) {
            return () -> { };
        }
        if (!s.tryAcquire()) {
            throw new EngineException("ENG-5001", EngineException.Kind.LIMIT, engine + " is answering " + limits.get(c) + " "
                    + c.name().toLowerCase() + " requests now, its limit; ask again shortly");
        }
        return s::release;
    }
}
