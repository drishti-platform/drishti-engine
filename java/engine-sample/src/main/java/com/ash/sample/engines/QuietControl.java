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
package com.ash.sample.engines;

import com.ash.engine.Answer;
import com.ash.engine.Control;
import com.ash.engine.EngineException;
import java.time.Instant;

/** The request's control for an ask made inside another call (the workbook's figures): cancel and deadline pass through; no interim answer is shown. */
final class QuietControl implements Control {

    private final Control outer;

    QuietControl(Control outer) {
        this.outer = outer;
    }

    @Override public boolean cancelled() { return outer.cancelled(); }
    @Override public void check() throws EngineException { outer.check(); }
    @Override public Instant deadline() { return outer.deadline(); }
    @Override public void progress(double fraction, String words) { outer.progress(fraction, words); }
    @Override public void interim(Answer answer) { }
}
