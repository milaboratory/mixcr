/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class Util {
    private Util() {
    }

    public static String printTwoColumns(List<String> left, List<String> right, int leftWidth, int rightWidth, int sep) {
        return printTwoColumns(left, right, leftWidth, rightWidth, sep, "");
    }

    public static String printTwoColumns(List<String> left, List<String> right, int leftWidth, int rightWidth, int sep, String betweenLines) {
        return printTwoColumns(0, left, right, leftWidth, rightWidth, sep, betweenLines);
    }

    public static String printTwoColumns(int offset, List<String> left, List<String> right, int leftWidth, int rightWidth, int sep, String betweenLines) {
        if (left.size() != right.size())
            throw new IllegalArgumentException();
        left = new ArrayList<>(left);
        right = new ArrayList<>(right);
        boolean breakOnNext;
        String spacer = spacer(sep), offsetSpacer = spacer(offset);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < left.size(); ++i) {
            String le = left.get(i), ri = right.get(i);
            breakOnNext = true;
            if (le.length() >= leftWidth && ri.length() >= rightWidth) {
                int leBr = lineBreakPos(le, leftWidth), riBr = lineBreakPos(ri, rightWidth);
                String l = le.substring(0, leBr), r = right.get(i).substring(0, riBr);
                String l1 = le.substring(leBr), r1 = right.get(i).substring(riBr);
                le = l;
                ri = r;
                left.add(i + 1, l1);
                right.add(i + 1, r1);
            } else if (le.length() >= leftWidth) {
                int leBr = lineBreakPos(le, leftWidth);
                String l = le.substring(0, leBr), l1 = le.substring(leBr);
                le = l;
                left.add(i + 1, l1);
                right.add(i + 1, "");
            } else if (ri.length() >= rightWidth) {
                int riBr = lineBreakPos(ri, rightWidth);
                String r = ri.substring(0, riBr), r1 = ri.substring(riBr);
                ri = r;
                right.add(i + 1, r1);
                left.add(i + 1, "");
            } else breakOnNext = false;
            sb.append(offsetSpacer).append(le).append(spacer)
                    .append(spacer(leftWidth - le.length())).append(ri).append('\n');
            if (!breakOnNext)
                sb.append(betweenLines);
        }
        assert left.size() == right.size();
        return sb.toString();
    }

    private static TIntObjectHashMap<String> spacesCache = new TIntObjectHashMap<>();

    public static synchronized String spacer(int sep) {
        String s = spacesCache.get(sep);
        if (s == null) {
            StringBuilder sb = new StringBuilder(sep);
            for (int i = 0; i < sep; ++i)
                sb.append(" ");
            spacesCache.put(sep, s = sb.toString());
        }
        return s;
    }

    private static int lineBreakPos(String str, int width) {
        int i = width - 1;
        for (; i >= 0; --i)
            if (str.charAt(i) == ' ')
                break;
        if (i <= 3)
            return width - 1;
        return i + 1;
    }

    public static Path getGlobalSettingsDir() {
        return null;
    }

    public static Path getLocalSettingsDir() {
        return Paths.get(System.getProperty("user.home"), ".mixcr");
    }
}
