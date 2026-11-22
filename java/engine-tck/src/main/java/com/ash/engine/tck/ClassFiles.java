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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A reader of the parts of a class file the kit needs (JDK 21 has no class-file API): every class it references, every field, method
 * and interface-method reference, and its own fields with their access flags. Enough for the closed-world link check and the static scan.
 */
public final class ClassFiles {

    /** A field, method or interface-method reference: owner (internal name), member name, descriptor. */
    public record Ref(String owner, String name, String descriptor) {}

    /** A field the class declares. */
    public record Field(String name, String descriptor, int access) {

        public boolean isStatic() {
            return (access & 0x0008) != 0;
        }

        public boolean isFinal() {
            return (access & 0x0010) != 0;
        }

        public boolean isSynthetic() {
            return (access & 0x1000) != 0;
        }
    }

    /** What one class file says. {@code classes}: internal names, element types of arrays included, sorted. */
    public record Info(String name, Set<String> classes, List<Ref> refs, List<Field> fields) {}

    private ClassFiles() {
    }

    /** Reads one class file; IOException when the bytes are not one. */
    public static Info read(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int n = in.readUnsignedShort();
        Object[] cp = new Object[n];
        int[] tag = new int[n];
        int[][] pair = new int[n][];
        for (int i = 1; i < n; i++) {
            int t = in.readUnsignedByte();
            tag[i] = t;
            switch (t) {
                case 1 -> cp[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    i++;
                }
                case 7, 8, 16, 19, 20 -> pair[i] = new int[] {in.readUnsignedShort()};
                case 9, 10, 11, 12, 17, 18 -> pair[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()};
                case 15 -> pair[i] = new int[] {in.readUnsignedByte(), in.readUnsignedShort()};
                default -> throw new IOException("an unknown constant pool tag " + t);
            }
        }
        Set<String> classes = new TreeSet<>();
        List<Ref> refs = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            switch (tag[i]) {
                case 7 -> addClass(classes, (String) cp[pair[i][0]]);
                case 9, 10, 11 -> {
                    String owner = (String) cp[pair[pair[i][0]][0]];
                    int[] nt = pair[pair[i][1]];
                    String desc = (String) cp[nt[1]];
                    refs.add(new Ref(owner.startsWith("[") ? element(owner) : owner, (String) cp[nt[0]], desc));
                    addDescriptor(classes, desc);
                }
                case 12 -> addDescriptor(classes, (String) cp[pair[i][1]]);
                case 16 -> addDescriptor(classes, (String) cp[pair[i][0]]);
                default -> { }
            }
        }
        in.readUnsignedShort();
        String self = (String) cp[pair[in.readUnsignedShort()][0]];
        in.readUnsignedShort();
        int ifs = in.readUnsignedShort();
        for (int i = 0; i < ifs; i++) {
            in.readUnsignedShort();
        }
        List<Field> fields = new ArrayList<>();
        int fc = in.readUnsignedShort();
        for (int i = 0; i < fc; i++) {
            int access = in.readUnsignedShort();
            String name = (String) cp[in.readUnsignedShort()];
            String desc = (String) cp[in.readUnsignedShort()];
            addDescriptor(classes, desc);
            fields.add(new Field(name, desc, access));
            int ac = in.readUnsignedShort();
            for (int a = 0; a < ac; a++) {
                in.readUnsignedShort();
                in.skipNBytes(in.readInt() & 0xFFFFFFFFL);
            }
        }
        int mc = in.readUnsignedShort();
        for (int i = 0; i < mc; i++) {
            in.readUnsignedShort();
            in.readUnsignedShort();
            addDescriptor(classes, (String) cp[in.readUnsignedShort()]);
            int ac = in.readUnsignedShort();
            for (int a = 0; a < ac; a++) {
                in.readUnsignedShort();
                in.skipNBytes(in.readInt() & 0xFFFFFFFFL);
            }
        }
        classes.remove(self);
        return new Info(self, classes, refs, fields);
    }

    private static void addClass(Set<String> out, String internal) {
        if (internal.startsWith("[")) {
            addDescriptor(out, internal);
        } else {
            out.add(internal);
        }
    }

    private static String element(String arrayDescriptor) {
        String e = arrayDescriptor.replaceFirst("^\\[+", "");
        return e.startsWith("L") ? e.substring(1, e.length() - 1) : e;
    }

    /** Every {@code L…;} type in a field or method descriptor. */
    static void addDescriptor(Set<String> out, String desc) {
        int i = 0;
        while ((i = desc.indexOf('L', i)) >= 0) {
            int end = desc.indexOf(';', i);
            if (end < 0) {
                return;
            }
            String name = desc.substring(i + 1, end);
            if (name.indexOf('<') < 0 && !name.isEmpty() && (i == 0 || "([);".indexOf(desc.charAt(i - 1)) >= 0 || Character.isUpperCase(desc.charAt(i - 1)))) {
                out.add(name);
            }
            i = end + 1;
        }
    }
}
