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

import com.ash.engine.inputs.RowInputs;
import com.ash.engine.Answer;
import com.ash.engine.Assembling;
import com.ash.engine.ColumnSchema;
import com.ash.engine.Descriptor;
import com.ash.engine.Descriptors;
import com.ash.engine.Engine;
import com.ash.engine.EngineException;
import com.ash.engine.Explaining;
import com.ash.engine.Incremental;
import com.ash.engine.Partitioned;
import com.ash.engine.Session;
import com.ash.engine.Status;
import com.ash.engine.Table;
import com.ash.engine.TableSchema;
import com.ash.engine.host.HostControl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The descriptor, contracts and lifecycle groups (sections 6.3, 6.4, 3.4, 5.2, 7, 8.5 of the specification). */
public final class Checks {

    private Checks() {
    }

    /** The descriptor group: the schema's rules (already applied when it was read) and those it cannot say. */
    public static List<Result> descriptor(KitHost host) {
        Descriptor d = host.descriptor();
        List<Result> out = new ArrayList<>();
        List<String> p = Descriptors.problems(d);
        out.add(Result.of("descriptor", d.id(), "rules", p.isEmpty() ? null : String.join("; ", p), null));
        try {
            Engine e = host.within(() -> host.subject().create());
            Descriptor said = host.within(e::descriptor);
            host.within(() -> {
                e.close();
                return null;
            });
            out.add(Result.of("descriptor", d.id(), "equals the file", said.equals(d) ? null : "Engine.descriptor() differs from the descriptor file", null));
            boolean assembling = e instanceof Assembling;
            out.add(Result.of("descriptor", d.id(), "engine capabilities", assembling == d.has("partition") ? null
                    : assembling ? "the engine implements Assembling without the capability partition" : "the capability partition needs the engine to implement Assembling", null));
        } catch (Exception ex) {
            out.add(Result.fail("descriptor", d.id(), "equals the file", "the engine cannot be created: " + ex));
        }
        return out;
    }

    /** The session's capability interfaces against the descriptor (rule [K] of 4.3). */
    static Result sessionCapabilities(Descriptor d, Session s, String subject) {
        List<String> p = new ArrayList<>();
        if ((s instanceof Explaining) != (d.has("explain") || d.has("workbook"))) {
            p.add("Explaining " + (s instanceof Explaining ? "is implemented without" : "is not implemented with") + " the capability explain");
        }
        if ((s instanceof Incremental) != d.has("incremental")) {
            p.add("Incremental " + (s instanceof Incremental ? "is implemented without" : "is not implemented with") + " the capability incremental");
        }
        if ((s instanceof Partitioned) != d.has("partition")) {
            p.add("Partitioned " + (s instanceof Partitioned ? "is implemented without" : "is not implemented with") + " the capability partition");
        }
        return Result.of("descriptor", subject, "session capabilities", p.isEmpty() ? null : String.join("; ", p), null);
    }

    /** The contracts group for one outcome: tables in the order asked, each exactly its output's schema, keys unique, rows in order. */
    public static Result answer(Descriptor d, Cases.Case c, Outcome o) {
        if (!o.answered()) {
            return Result.ok("contracts", c.name(), "answer", "a refusal");
        }
        Answer a = o.answer();
        List<String> p = new ArrayList<>();
        if (a.status() == Status.COMPLETE && a.tables().size() != c.request().outputs().size()) {
            p.add("COMPLETE with " + a.tables().size() + " tables for " + c.request().outputs().size() + " outputs asked");
        }
        if (a.status() == Status.NOT_FOUND && !a.tables().isEmpty()) {
            p.add("NOT_FOUND with tables");
        }
        if (a.status() != Status.COMPLETE && a.missing().isEmpty()) {
            p.add(a.status() + " without a missing input");
        }
        if (a.stamp() == null) {
            p.add("no stamp (build answers with Answers or Stamps.of)");
        }
        int at = 0;
        for (Table t : a.tables()) {
            String name = t.schema().name();
            while (at < c.request().outputs().size() && !c.request().outputs().get(at).equals(name)) {
                at++;
            }
            if (at >= c.request().outputs().size()) {
                p.add("the table " + name + " is not an output asked, or not in the order asked");
                break;
            }
            TableSchema want = d.schema(name);
            if (!shape(want).equals(shape(t.schema()))) {
                p.add(name + ": columns " + shape(t.schema()) + ", the descriptor declares " + shape(want));
                continue;
            }
            if (!want.key().equals(t.schema().key()) || !want.order().equals(t.schema().order())) {
                p.add(name + ": key or order differs from the descriptor's");
            }
            if ("row".equals(d.output(name).shape()) && t.rows() != 1) {
                p.add(name + " is a row output with " + t.rows() + " rows");
            }
            for (ColumnSchema col : t.schema().columns()) {
                if ("number".equals(col.type()) && !col.nullable()) {
                    double[] v = t.numbers(col.name());
                    for (int r = 0; r < t.rows(); r++) {
                        if (Double.isNaN(v[r]) || Double.isInfinite(v[r])) {
                            p.add(name + "." + col.name() + " row " + (r + 1) + " is " + v[r] + " in a non-null number column");
                            break;
                        }
                    }
                }
            }
        }
        return Result.of("contracts", c.name(), "answer", p.isEmpty() ? null : String.join("; ", p), null);
    }

    private static List<String> shape(TableSchema s) {
        return s.columns().stream().map(c -> c.name() + ":" + c.type() + (c.nullable() ? "?" : "")).toList();
    }

    /**
     * The lifecycle group on one case: close twice is harmless, a closed session holds nothing, two instances are apart, a cancelled or
     * late request stops within the declared latency, a budget too small is a refusal in words and the engine stays usable.
     */
    public static List<Result> lifecycle(KitHost host, Cases.Case c, Outcome base) {
        Descriptor d = host.descriptor();
        List<Result> out = new ArrayList<>();
        String s = c.name();
        try {
            Engine never = host.within(() -> host.subject().create());
            host.within(() -> {
                never.close();
                never.close();
                return null;
            });
            out.add(Result.ok("lifecycle", s, "close without open", "harmless, twice"));
        } catch (Exception e) {
            out.add(Result.fail("lifecycle", s, "close without open", e.toString()));
        }
        try (KitHost.Opened a = host.open(c, KitHost.Variation.PLAIN); KitHost.Opened b = host.open(c, KitHost.Variation.PLAIN)) {
            out.add(sessionCapabilities(d, a.session(), s));
            if (!c.changes().isEmpty()) {
                a.apply(c.changes());
                b.apply(c.changes());
            }
            String diff = Compare.same(base, a.ask());
            diff = diff != null ? diff : Compare.same(base, b.ask());
            Session sa = a.session();
            host.within(() -> {
                sa.close();
                sa.close();
                return null;
            });
            long held = host.within(sa::memoryBytes);
            diff = diff != null ? diff : Compare.same(base, b.ask());
            out.add(Result.of("lifecycle", s, "two instances apart", diff, base.hash()));
            out.add(Result.of("lifecycle", s, "close", held == 0 ? null : "memoryBytes() is " + held + " after close", null));
        } catch (Exception e) {
            out.add(Result.fail("lifecycle", s, "two instances apart", e.toString()));
        }
        Duration latency = d.limits().cancelLatency();
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            if (!c.changes().isEmpty()) {
                o.apply(c.changes());
            }
            HostControl cancelled = o.control();
            cancelled.cancel();
            long t0 = System.nanoTime();
            Outcome x = o.ask(o.caseVisibility(), c.request().seed(), cancelled);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean ok = (x.answered() || "ENG-5010".equals(x.code()) || x.code().equals(base.code())) && ms <= Math.max(latency.toMillis(), 1) * 2 + 50;
            out.add(Result.of("lifecycle", s, "cancel", ok ? null : "a cancelled request gave " + x.brief() + " after " + ms + " ms (declared "
                    + latency.toMillis() + " ms)", null));
            HostControl late = new HostControl(Instant.EPOCH, Clock.systemUTC(), true);
            Outcome y = o.ask(o.caseVisibility(), c.request().seed(), late);
            boolean ok2 = y.answered() || "ENG-5011".equals(y.code()) || y.code().equals(base.code());
            out.add(Result.of("lifecycle", s, "deadline", ok2 ? null : "a request past its deadline gave " + y.brief(), null));
            String diff = Compare.same(base, o.ask());
            out.add(Result.of("lifecycle", s, "usable after cancel", diff, base.hash()));
        } catch (Exception e) {
            out.add(Result.fail("lifecycle", s, "cancel", e.toString()));
        }
        out.add(budget(host, c, base));
        return out;
    }

    private static Result budget(KitHost host, Cases.Case c, Outcome base) {
        try (KitHost.Opened o = host.open(c, KitHost.Variation.PLAIN)) {
            Engine e = o.engine();
            Session first = o.session();
            host.within(() -> {
                first.close();
                return null;
            });
            long before = o.context().budget().used();
            o.context().budget().limit(1);
            String words;
            try {
                Session tight = host.within(() -> e.session(com.ash.engine.Snapshot.of(c.given().snapshot()), inputsOf(host, c), o.control()));
                o.session = tight;
                Outcome x = c.changes().isEmpty() ? o.ask() : null;
                if (x != null && !x.answered() && !"ENG-5002".equals(x.code()) && !x.code().equals(base.code())) {
                    return Result.fail("lifecycle", c.name(), "budget", "a budget of one byte gave " + x.brief() + ", not ENG-5002 or an answer");
                }
                words = x == null || x.answered() ? "the engine reserves under a byte for this case" : "ENG-5002 in words, then usable";
                host.within(() -> {
                    tight.close();
                    return null;
                });
            } catch (EngineException ex) {
                if (!"ENG-5002".equals(ex.code())) {
                    return Result.fail("lifecycle", c.name(), "budget", "a budget of one byte: " + ex.code() + " " + ex.getMessage() + ", not ENG-5002");
                }
                words = "ENG-5002 at open, in words, then usable";
            } catch (OutOfMemoryError oom) {
                return Result.fail("lifecycle", c.name(), "budget", "an out-of-memory error, not a refusal in words");
            }
            long after = o.context().budget().used();
            if (after != before) {
                return Result.fail("lifecycle", c.name(), "budget", (after - before) + " bytes stay reserved after the session closed");
            }
            o.context().budget().limit(1L << 30);
            o.session = host.within(() -> e.session(com.ash.engine.Snapshot.of(c.given().snapshot()), inputsOf(host, c), o.control()));
            if (!c.changes().isEmpty() && o.apply(c.changes()) != null) {
                return Result.fail("lifecycle", c.name(), "budget", "the changes did not apply after the refusal");
            }
            String diff = Compare.same(base, o.ask());
            return Result.of("lifecycle", c.name(), "budget", diff == null ? null : "after the refusal: " + diff, diff == null ? base.hash() : null)
                    .withWords(diff == null ? words : null);
        } catch (Exception ex) {
            return Result.fail("lifecycle", c.name(), "budget", ex.toString());
        }
    }

    private static RowInputs inputsOf(KitHost host, Cases.Case c) {
        return new RowInputs(host.descriptor(), c.given().rows(), c.given().openTables(), RowInputs.Delivery.PLAIN, host.recorder(), null);
    }
}
