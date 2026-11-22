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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The strict YAML reader of descriptors, engine files and kit cases: one document of block and flow mappings and sequences, plain,
 * quoted and block ({@code |}, {@code >}) scalars, and comments. Refused, with the line: a repeated key, a tag, an anchor or alias, a tab
 * in indentation, a second document. Plain scalars resolve as YAML 1.2's core schema does: {@code null} and {@code ~}, {@code true} and
 * {@code false}, integers (Long), floats (Double), anything else text. Mappings keep their order (LinkedHashMap). JDK only.
 */
public final class StrictYaml {

    private static final Pattern INT = Pattern.compile("[-+]?[0-9]+");
    private static final Pattern FLOAT = Pattern.compile("[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?");

    private final List<Line> lines = new ArrayList<>();
    private final String[] raw;
    private int at;

    private record Line(int no, int indent, String text) {}

    private StrictYaml(String source) {
        raw = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean started = false;
        for (int i = 0; i < raw.length; i++) {
            String l = raw[i];
            int n = 0;
            while (n < l.length() && l.charAt(n) == ' ') {
                n++;
            }
            String body = stripComment(l.substring(n)).stripTrailing();
            if (body.isEmpty()) {
                continue;
            }
            if (n < l.length() && l.charAt(n) == '\t') {
                throw error(i + 1, "a tab in the indentation");
            }
            if (body.equals("---")) {
                if (started) {
                    throw error(i + 1, "a second document");
                }
                started = true;
                continue;
            }
            if (body.equals("...")) {
                break;
            }
            started = true;
            lines.add(new Line(i + 1, n, body));
        }
    }

    /** The document as plain data: Map (ordered), List, String, Long, Double, Boolean or null (an empty document). */
    public static Object read(String source) {
        StrictYaml y = new StrictYaml(source);
        if (y.lines.isEmpty()) {
            return null;
        }
        Line first = y.lines.get(0);
        Object v;
        if (first.text().startsWith("{") || first.text().startsWith("[")) {
            y.at = 1;                                          // a flow document: one JSON line reads the same way
            v = y.value(first.text(), first, -1);
        } else {
            v = y.block(first.indent());
        }
        if (y.at < y.lines.size()) {
            Line l = y.lines.get(y.at);
            throw error(l.no(), "unexpected indentation or content: " + l.text());
        }
        return v;
    }

    /** As {@link #read}, for a document whose top is a mapping; IllegalArgumentException otherwise. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String source) {
        Object v = read(source);
        if (!(v instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("line 1: the document is not a mapping");
        }
        return (Map<String, Object>) v;
    }

    private static IllegalArgumentException error(int line, String words) {
        return new IllegalArgumentException("line " + line + ": " + words);
    }

    private Object block(int indent) {
        Line l = lines.get(at);
        if (isItem(l.text())) {
            return sequence(indent);
        }
        return mapping(indent);
    }

    private static boolean isItem(String t) {
        return t.equals("-") || t.startsWith("- ");
    }

    private Map<String, Object> mapping(int indent) {
        Map<String, Object> out = new LinkedHashMap<>();
        while (at < lines.size()) {
            Line l = lines.get(at);
            if (l.indent() < indent) {
                break;
            }
            if (l.indent() > indent) {
                throw error(l.no(), "unexpected indentation: " + l.text());
            }
            if (isItem(l.text())) {
                break;
            }
            int colon = mapColon(l.text());
            if (colon < 0) {
                throw error(l.no(), "not a 'key: value' line: " + l.text());
            }
            String key = keyText(l.text().substring(0, colon).strip(), l.no());
            String rest = l.text().substring(colon + 1).strip();
            at++;
            if (out.containsKey(key)) {
                throw error(l.no(), "the key '" + key + "' is repeated");
            }
            out.put(key, value(rest, l, indent));
        }
        return out;
    }

    private List<Object> sequence(int indent) {
        List<Object> out = new ArrayList<>();
        while (at < lines.size()) {
            Line l = lines.get(at);
            if (l.indent() < indent || l.indent() == indent && !isItem(l.text())) {
                break;
            }
            if (l.indent() > indent) {
                throw error(l.no(), "unexpected indentation: " + l.text());
            }
            String item = l.text().equals("-") ? "" : l.text().substring(2);
            int lead = 0;
            while (lead < item.length() && item.charAt(lead) == ' ') {
                lead++;
            }
            item = item.substring(lead);
            if (item.isEmpty()) {
                at++;
                if (at < lines.size() && lines.get(at).indent() > indent) {
                    out.add(block(lines.get(at).indent()));
                } else {
                    out.add(null);
                }
            } else if (mapColon(item) >= 0 && !item.startsWith("{") && !item.startsWith("[")) {
                lines.set(at, new Line(l.no(), indent + 2 + lead, item));       // "- key: v": a mapping indented at the key
                out.add(mapping(indent + 2 + lead));
            } else {
                at++;
                out.add(value(item, l, indent));
            }
        }
        return out;
    }

    private Object value(String rest, Line l, int indent) {
        if (rest.isEmpty()) {
            if (at < lines.size()) {
                Line next = lines.get(at);
                if (next.indent() > indent) {
                    return block(next.indent());
                }
                if (next.indent() == indent && isItem(next.text())) {
                    return sequence(indent);
                }
            }
            return null;
        }
        char c = rest.charAt(0);
        if (c == '!' || c == '&' || c == '*') {
            throw error(l.no(), "tags, anchors and aliases are not read: " + rest);
        }
        if (c == '|' || c == '>') {
            return blockScalar(rest, l, indent);
        }
        if (c == '{' || c == '[') {
            StringBuilder text = new StringBuilder(rest);
            while (!balanced(text) && at < lines.size()) {
                text.append(' ').append(lines.get(at).text());
                at++;
            }
            Flow f = new Flow(text.toString(), l.no());
            Object v = f.value();
            f.space();
            if (f.i < f.s.length()) {
                throw error(l.no(), "text after a flow collection: " + f.s.substring(f.i));
            }
            return v;
        }
        return scalar(rest, l.no());
    }

    private Object blockScalar(String head, Line l, int indent) {
        boolean folded = head.charAt(0) == '>';
        boolean strip = head.contains("-");
        int from = l.no();                                    // raw index of the next physical line
        List<String> body = new ArrayList<>();
        int bodyIndent = -1;
        int last = from;
        for (int i = from; i < raw.length; i++) {
            String r = raw[i];
            if (r.isBlank()) {
                body.add("");
                continue;
            }
            int n = 0;
            while (n < r.length() && r.charAt(n) == ' ') {
                n++;
            }
            if (n <= indent) {
                break;
            }
            if (bodyIndent < 0) {
                bodyIndent = n;
            }
            if (n < bodyIndent) {
                break;
            }
            body.add(r.substring(bodyIndent));
            last = i + 1;
        }
        while (at < lines.size() && lines.get(at).no() <= last) {
            at++;
        }
        while (!body.isEmpty() && body.get(body.size() - 1).isEmpty()) {
            body.remove(body.size() - 1);
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < body.size(); i++) {
            String s = body.get(i);
            if (i > 0) {
                b.append(folded && !s.isEmpty() && !body.get(i - 1).isEmpty() ? " " : "\n");
            }
            b.append(s);
        }
        return strip || b.length() == 0 ? b.toString() : b + "\n";
    }

    private static boolean balanced(CharSequence s) {
        int depth = 0;
        char q = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q != 0) {
                if (c == q) {
                    if (q == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        q = 0;
                    }
                } else if (c == '\\' && q == '"') {
                    i++;
                }
            } else if (c == '"' || c == '\'') {
                q = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return depth <= 0;
    }

    /** The index of the ':' that ends a key (followed by a space or the end), outside quotes and flow brackets; -1 when none. */
    static int mapColon(String t) {
        int depth = 0;
        char q = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (q != 0) {
                if (c == q) {
                    q = 0;
                } else if (c == '\\' && q == '"') {
                    i++;
                }
            } else if ((c == '"' || c == '\'') && (i == 0)) {
                q = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ':' && depth == 0 && (i + 1 == t.length() || t.charAt(i + 1) == ' ')) {
                return i;
            }
        }
        return -1;
    }

    private static String keyText(String k, int line) {
        if (k.isEmpty()) {
            throw error(line, "an empty key");
        }
        Object v = k.startsWith("\"") || k.startsWith("'") ? quoted(k, line) : k;
        return v.toString();
    }

    static String stripComment(String t) {
        char q = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (q != 0) {
                if (c == q) {
                    q = 0;
                } else if (c == '\\' && q == '"') {
                    i++;
                }
            } else if ((c == '"' || c == '\'') && (i == 0 || " ,[{:-".indexOf(t.charAt(i - 1)) >= 0)) {
                q = c;
            } else if (c == '#' && (i == 0 || t.charAt(i - 1) == ' ')) {
                return t.substring(0, i);
            }
        }
        return t;
    }

    static Object scalar(String s, int line) {
        s = s.strip();
        if (s.startsWith("\"") || s.startsWith("'")) {
            return quoted(s, line);
        }
        if (s.startsWith("!") || s.startsWith("&") || s.startsWith("*")) {
            throw error(line, "tags, anchors and aliases are not read: " + s);
        }
        switch (s) {
            case "null", "Null", "NULL", "~" -> {
                return null;
            }
            case "true", "True", "TRUE" -> {
                return Boolean.TRUE;
            }
            case "false", "False", "FALSE" -> {
                return Boolean.FALSE;
            }
            case ".inf", "+.inf", ".Inf" -> {
                return Double.POSITIVE_INFINITY;
            }
            case "-.inf", "-.Inf" -> {
                return Double.NEGATIVE_INFINITY;
            }
            case ".nan", ".NaN" -> {
                return Double.NaN;
            }
            default -> {
                // a plain scalar
            }
        }
        if (INT.matcher(s).matches()) {
            try {
                return Long.parseLong(s.startsWith("+") ? s.substring(1) : s);
            } catch (NumberFormatException e) {
                throw error(line, "an integer out of range: " + s);
            }
        }
        if (FLOAT.matcher(s).matches()) {
            return Double.parseDouble(s);
        }
        return s;
    }

    private static String quoted(String s, int line) {
        char q = s.charAt(0);
        if (s.length() < 2 || s.charAt(s.length() - 1) != q) {
            throw error(line, "an unclosed quote: " + s);
        }
        String body = s.substring(1, s.length() - 1);
        if (q == '\'') {
            return body.replace("''", "'");
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (++i >= body.length()) {
                throw error(line, "a trailing backslash: " + s);
            }
            char e = body.charAt(i);
            switch (e) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case '"' -> b.append('"');
                case '\\' -> b.append('\\');
                case '/' -> b.append('/');
                case '0' -> b.append('\0');
                case 'u' -> {
                    b.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw error(line, "an unknown escape \\" + e);
            }
        }
        return b.toString();
    }

    /** A flow collection on one logical line. */
    private static final class Flow {
        final String s;
        final int line;
        int i;

        Flow(String s, int line) {
            this.s = s;
            this.line = line;
        }

        void space() {
            while (i < s.length() && s.charAt(i) == ' ') {
                i++;
            }
        }

        Object value() {
            space();
            if (i >= s.length()) {
                throw error(line, "a flow collection ends early");
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                space();
                if (peek() == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    space();
                    String key = token(true).toString();
                    space();
                    if (peek() != ':') {
                        throw error(line, "a flow mapping key without ':' near: " + s.substring(Math.max(0, i - 20)));
                    }
                    i++;
                    space();
                    Object v = peek() == ',' || peek() == '}' ? null : value();
                    if (m.containsKey(key)) {
                        throw error(line, "the key '" + key + "' is repeated");
                    }
                    m.put(key, v);
                    space();
                    char n = next();
                    if (n == '}') {
                        return m;
                    }
                    if (n != ',') {
                        throw error(line, "expected ',' or '}' in a flow mapping near: " + s.substring(Math.max(0, i - 20)));
                    }
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                space();
                if (peek() == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    space();
                    char n = next();
                    if (n == ']') {
                        return l;
                    }
                    if (n != ',') {
                        throw error(line, "expected ',' or ']' in a flow sequence near: " + s.substring(Math.max(0, i - 20)));
                    }
                }
            }
            return token(false);
        }

        private char peek() {
            return i < s.length() ? s.charAt(i) : 0;
        }

        private char next() {
            if (i >= s.length()) {
                throw error(line, "a flow collection ends early");
            }
            return s.charAt(i++);
        }

        /** A scalar inside a flow collection: quoted, or plain up to ',', ']', '}' (and ': ' for a key). */
        private Object token(boolean key) {
            char c = peek();
            if (c == '"' || c == '\'') {
                int start = i++;
                while (i < s.length()) {
                    char d = s.charAt(i);
                    if (d == '\\' && c == '"') {
                        i += 2;
                        continue;
                    }
                    if (d == c) {
                        if (c == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    i++;
                }
                i++;
                return quoted(s.substring(start, Math.min(i, s.length())), line);
            }
            int start = i;
            int depth = 0;
            while (i < s.length()) {
                char d = s.charAt(i);
                if (depth == 0 && (d == ',' || d == ']' || d == '}')) {
                    break;
                }
                if (d == ':' && (i + 1 >= s.length() || " ,]}".indexOf(s.charAt(i + 1)) >= 0) && (key || depth == 0 && isKeyContext())) {
                    break;
                }
                if (d == '(') {
                    depth++;
                } else if (d == ')') {
                    depth--;
                }
                i++;
            }
            String t = s.substring(start, i).strip();
            return key ? t : scalar(t, line);
        }

        private boolean isKeyContext() {
            return false;
        }
    }
}
