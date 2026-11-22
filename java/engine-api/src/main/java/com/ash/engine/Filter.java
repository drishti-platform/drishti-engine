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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** A row filter over an input's scope columns. The whole tree is data: no code. */
public sealed interface Filter {

    /** Every row. Never implied: a host gives it explicitly. */
    Filter ALL = new All();

    /** True when a row whose scope columns read {@code value} (by column name) passes. A missing column reads null. */
    boolean test(java.util.function.Function<String, String> value);

    /** The columns the filter names. */
    Set<String> columns();

    /** Every row. */
    record All() implements Filter {
        @Override public boolean test(java.util.function.Function<String, String> value) { return true; }
        @Override public Set<String> columns() { return Set.of(); }
    }

    /** The column equals the value. */
    record Eq(String column, String value) implements Filter {
        @Override public boolean test(java.util.function.Function<String, String> v) { return value.equals(v.apply(column)); }
        @Override public Set<String> columns() { return Set.of(column); }
    }

    /** The column is one of the values. */
    record In(String column, Set<String> values) implements Filter {
        public In {
            values = Set.copyOf(values);
        }
        @Override public boolean test(java.util.function.Function<String, String> v) { String x = v.apply(column); return x != null && values.contains(x); }
        @Override public Set<String> columns() { return Set.of(column); }
    }

    /** The column is within the bounds, compared as text; either bound may be null (open). */
    record Range(String column, String fromInclusive, String toInclusive) implements Filter {
        @Override public boolean test(java.util.function.Function<String, String> v) {
            String x = v.apply(column);
            return x != null && (fromInclusive == null || x.compareTo(fromInclusive) >= 0) && (toInclusive == null || x.compareTo(toInclusive) <= 0);
        }
        @Override public Set<String> columns() { return Set.of(column); }
    }

    /** Every filter passes. */
    record And(List<Filter> all) implements Filter {
        public And {
            all = List.copyOf(all);
        }
        @Override public boolean test(java.util.function.Function<String, String> v) { return all.stream().allMatch(f -> f.test(v)); }
        @Override public Set<String> columns() { Set<String> s = new TreeSet<>(); all.forEach(f -> s.addAll(f.columns())); return s; }
    }

    /** Some filter passes. */
    record Or(List<Filter> any) implements Filter {
        public Or {
            any = List.copyOf(any);
        }
        @Override public boolean test(java.util.function.Function<String, String> v) { return any.stream().anyMatch(f -> f.test(v)); }
        @Override public Set<String> columns() { Set<String> s = new TreeSet<>(); any.forEach(f -> s.addAll(f.columns())); return s; }
    }

    /** The filter does not pass. */
    record Not(Filter filter) implements Filter {
        @Override public boolean test(java.util.function.Function<String, String> v) { return !filter.test(v); }
        @Override public Set<String> columns() { return filter.columns(); }
    }
}
