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
package com.ash.engine.tck;

import com.ash.rachana.Rachana;
import com.ash.rachana.api.Node;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.el.Values;
import com.ash.rachana.format.Formats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Rachana Core's own evaluator at language level 1.2: the oracle the kit checks explanation formulas and push-down expressions against. */
public final class RachanaEl implements ElOracle {

    private final ElCompiler compiler = Rachana.at("1.2", null).compiler();
    private final Formats formats = Formats.defaults();
    private final Map<String, Expr> compiled = new ConcurrentHashMap<>();

    @Override
    public Object eval(String expression, Map<String, Object> document) {
        Expr e = compiled.computeIfAbsent(expression, compiler::compile);
        return plain(e.eval(EvalContext.of(Node.of(document), formats)));
    }

    @Override
    public String name() {
        return "Rachana Core EL (" + Rachana.class.getPackage().getImplementationVersion() + ")";
    }

    private static Object plain(Object v) {
        Object s = Values.simplify(v);
        if (s instanceof Node n) {
            return switch (n.kind()) {
                case NUMBER, STRING, BOOLEAN -> Values.simplify(n);
                case NULL, MISSING -> null;
                case ARRAY -> {
                    List<Object> out = new ArrayList<>();
                    for (int i = 0; i < n.size(); i++) {
                        out.add(plain(n.get(i)));
                    }
                    yield out;
                }
                default -> Values.text(n);
            };
        }
        return s;
    }
}
