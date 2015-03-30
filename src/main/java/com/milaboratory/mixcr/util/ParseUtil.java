/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.util;

import com.milaboratory.util.IntArrayList;
import gnu.trove.map.hash.TCharIntHashMap;

public final class ParseUtil {
    private ParseUtil() {
    }

    public static String[] splitWithBrackets(String string, char splitChar, String brackets) {
        return splitWithBrackets(string, splitChar, new BracketsInfo(brackets));
    }

    public static String[] splitWithBrackets(String string, char splitChar, BracketsInfo brackets) {
        IntArrayList splitPoints = new IntArrayList();
        splitPoints.push(-1);
        BracketsProcessor bracketsProcessor = brackets.createBracketsProcessor();
        for (int i = 0; i < string.length(); ++i) {
            char c = string.charAt(i);
            if (!bracketsProcessor.process(c)) {
                if (c == splitChar && bracketsProcessor.getDepth() == 0)
                    splitPoints.push(i);
            }
        }
        bracketsProcessor.finish();
        splitPoints.push(string.length());
        int size = splitPoints.size() - 1;
        String[] result = new String[size];
        for (int i = 0; i < size; ++i)
            result[i] = string.substring(splitPoints.get(i) + 1, splitPoints.get(i + 1));
        return result;
    }

    public static final class BracketsInfo {
        private final TCharIntHashMap bracketsMap = new TCharIntHashMap();

        public BracketsInfo(String brackets) {
            if (brackets.length() % 2 != 0)
                throw new IllegalArgumentException();
            for (int i = brackets.length() / 2 - 1; i >= 0; --i) {
                bracketsMap.put(brackets.charAt(i * 2), i + 1);
                bracketsMap.put(brackets.charAt(i * 2 + 1), -i - 1);
            }
        }

        BracketsProcessor createBracketsProcessor() {
            return new BracketsProcessor(bracketsMap);
        }
    }

    private static final class BracketsProcessor {
        private final TCharIntHashMap bracketsMap;
        IntArrayList types = new IntArrayList();

        private BracketsProcessor(TCharIntHashMap bracketsMap) {
            this.bracketsMap = bracketsMap;
        }

        public boolean process(char c) {
            int v = bracketsMap.get(c);
            if (v == 0)
                return false;
            else {
                if (v < 0) {
                    if (types.size() == 0)
                        throw new ParserException("Closing bracket '" + c + "' before any opening bracket.");
                    if (types.pop() != -v)
                        throw new ParserException("Unbalanced bracket '" + c + "'.");
                    return true;
                } else {
                    types.push(v);
                    return true;
                }
            }
        }

        public void finish() {
            if (getDepth() != 0)
                throw new ParserException("Unbalanced brackets.");
        }

        public int getDepth() {
            return types.size();
        }
    }
}
