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
package com.ash.engine.text;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical JSON text of plain data (maps with keys sorted, no insignificant space, doubles as the shortest text that reads back to the
 * same bits) and SHA-384 hashes as lower-case hex: the one form of every hash in a stamp, a certificate and a results file. JDK only.
 */
public final class Canonical {

    private Canonical() {
    }

    /** The canonical JSON text of {@code value}: Map, Collection, array of primitives, String, Number, Boolean, null. */
    public static String json(Object value) {
        StringBuilder b = new StringBuilder();
        write(b, value);
        return b.toString();
    }

    /** SHA-384 of the UTF-8 bytes of {@code text}, lower-case hex. */
    public static String sha384(String text) {
        return sha384(text.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-384 of {@code bytes}, lower-case hex. */
    public static String sha384(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-384").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("the JDK has no SHA-384", e);
        }
    }

    /** The raw bits of a double as 16 lower-case hex digits with a {@code 0x} prefix: the kit's exact form of a number. */
    public static String bits(double d) {
        return "0x" + String.format("%016x", Double.doubleToRawLongBits(d));
    }

    private static void write(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String s) {
            text(b, s);
        } else if (v instanceof Boolean x) {
            b.append(x);
        } else if (v instanceof Double d) {
            number(b, d);
        } else if (v instanceof Float f) {
            number(b, f.doubleValue());
        } else if (v instanceof Number n) {
            b.append(n);
        } else if (v instanceof Map<?, ?> m) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            m.forEach((k, x) -> sorted.put(String.valueOf(k), x));
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                b.append(first ? "" : ",");
                first = false;
                text(b, e.getKey());
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof Collection<?> c) {
            b.append('[');
            boolean first = true;
            for (Object x : c) {
                b.append(first ? "" : ",");
                first = false;
                write(b, x);
            }
            b.append(']');
        } else if (v instanceof double[] a) {
            b.append('[');
            for (int i = 0; i < a.length; i++) {
                b.append(i == 0 ? "" : ",");
                number(b, a[i]);
            }
            b.append(']');
        } else if (v instanceof long[] a) {
            b.append('[');
            for (int i = 0; i < a.length; i++) {
                b.append(i == 0 ? "" : ",").append(a[i]);
            }
            b.append(']');
        } else if (v instanceof Object[] a) {
            write(b, java.util.Arrays.asList(a));
        } else {
            text(b, v.toString());
        }
    }

    private static void number(StringBuilder b, double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            text(b, bits(d));
        } else if (d == Math.rint(d) && Math.abs(d) < 1e15 && !(d == 0 && 1 / d < 0)) {
            b.append((long) d);
        } else {
            b.append(Double.toString(d));
        }
    }

    private static void text(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }
}
