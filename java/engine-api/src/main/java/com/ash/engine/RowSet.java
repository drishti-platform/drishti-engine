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

import java.util.Arrays;
import java.util.BitSet;

/**
 * Row ordinals of one snapshot version (specification 18.3, 19.1): a visibility, a scope, a node, a basket. The host resolves every scope
 * kind to one; the engine sees ordinals only. Immutable; iterated in ascending order, which is the reduction order of 19.4.
 */
public final class RowSet {

    private final BitSet bits;
    private final int size;

    private RowSet(BitSet bits) {
        this.bits = bits;
        this.size = bits.cardinality();
    }

    /** Rows {@code from} (inclusive) to {@code to} (exclusive). */
    public static RowSet range(int from, int to) {
        BitSet b = new BitSet(Math.max(to, 0));
        if (to > from) {
            b.set(from, to);
        }
        return new RowSet(b);
    }

    /** Every row of a table of {@code rows} rows. */
    public static RowSet all(int rows) {
        return range(0, rows);
    }

    /** The given ordinals, in any order, without repeats counted twice. */
    public static RowSet of(int... rows) {
        BitSet b = new BitSet();
        for (int r : rows) {
            if (r < 0) {
                throw new IllegalArgumentException("a row ordinal is not negative: " + r);
            }
            b.set(r);
        }
        return new RowSet(b);
    }

    /** A copy of {@code bits} as a row set. */
    public static RowSet of(BitSet bits) {
        return new RowSet((BitSet) bits.clone());
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(int row) {
        return row >= 0 && bits.get(row);
    }

    /** The ordinals, ascending. */
    public int[] toArray() {
        return bits.stream().toArray();
    }

    /** Each run of consecutive rows, ascending: {@code from} inclusive, {@code to} exclusive. */
    public void forEachRun(RunConsumer c) {
        int from = bits.nextSetBit(0);
        while (from >= 0) {
            int to = bits.nextClearBit(from);
            c.run(from, to);
            from = bits.nextSetBit(to);
        }
    }

    public RowSet and(RowSet o) {
        BitSet b = (BitSet) bits.clone();
        b.and(o.bits);
        return new RowSet(b);
    }

    public RowSet or(RowSet o) {
        BitSet b = (BitSet) bits.clone();
        b.or(o.bits);
        return new RowSet(b);
    }

    public RowSet andNot(RowSet o) {
        BitSet b = (BitSet) bits.clone();
        b.andNot(o.bits);
        return new RowSet(b);
    }

    /** One run of rows. */
    @FunctionalInterface
    public interface RunConsumer {
        void run(int from, int to);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RowSet r && r.bits.equals(bits);
    }

    @Override
    public int hashCode() {
        return bits.hashCode();
    }

    @Override
    public String toString() {
        int[] a = toArray();
        return "RowSet" + (a.length <= 16 ? Arrays.toString(a) : "[" + a.length + " rows]");
    }
}
