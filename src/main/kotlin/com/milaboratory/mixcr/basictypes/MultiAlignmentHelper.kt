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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.MutationType;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.Alphabet;
import com.milaboratory.core.sequence.Sequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.util.BitArray;
import com.milaboratory.util.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.milaboratory.core.mutations.Mutation.RAW_MUTATION_TYPE_DELETION;
import static com.milaboratory.core.mutations.Mutation.RAW_MUTATION_TYPE_SUBSTITUTION;

public class MultiAlignmentHelper {
    // subject / queries nomenclature seems to be swapped here...
    // subject here corresponds to sequence2 from alignments (so, the query sequence)
    // queries here corresponds to sequence1 from alignments (so, the reference sequence)

    int minimalPositionWidth = 0;
    final String subject;
    final String[] queries;
    final int[] subjectPositions;
    final int[][] queryPositions;
    final BitArray[] match;
    final List<String> annotationStrings = new ArrayList<>();
    final List<String> annotationStringTitles = new ArrayList<>();

    String subjectLeftTitle;
    final String[] queryLeftTitles;

    String subjectRightTitle;
    final String[] queryRightTitles;

    private MultiAlignmentHelper(String subject, String[] queries, int[] subjectPositions,
                                 int[][] queryPositions, BitArray[] match,
                                 String subjectLeftTitle, String[] queryLeftTitles,
                                 String subjectRightTitle, String[] queryRightTitles) {
        this.subject = subject;
        this.queries = queries;
        this.subjectPositions = subjectPositions;
        this.queryPositions = queryPositions;
        this.match = match;
        this.subjectLeftTitle = subjectLeftTitle;
        this.queryLeftTitles = queryLeftTitles;
        this.subjectRightTitle = subjectRightTitle;
        this.queryRightTitles = queryRightTitles;
    }

    private MultiAlignmentHelper(String subject, String[] queries, int[] subjectPositions, int[][] queryPositions,
                                 BitArray[] match) {
        this(subject, queries, subjectPositions, queryPositions, match, "", new String[queries.length],
                "", new String[queries.length]);
    }

    public String getSubject() {
        return subject;
    }

    public String getQuery(int idx) {
        return queries[idx];
    }

    public int[] getSubjectPositions() {
        return subjectPositions;
    }

    public int[][] getQueryPositions() {
        return queryPositions;
    }

    public BitArray[] getMatch() {
        return match;
    }

    public String getSubjectLeftTitle() {
        return subjectLeftTitle;
    }

    public String getSubjectRightTitle() {
        return subjectRightTitle;
    }

    public String getQueryLeftTitle(int i) {
        return queryLeftTitles[i];
    }

    public String getQueryRightTitle(int i) {
        return queryRightTitles[i];
    }

    public int getActualPositionWidth() {
        int ret = ("" + getSubjectFrom()).length();
        for (int i = 0; i < queries.length; i++)
            ret = Math.max(ret, ("" + getQueryFrom(i)).length());
        return ret;
    }

    public void setMinimalPositionWidth(int minimalPositionWidth) {
        this.minimalPositionWidth = minimalPositionWidth;
    }

    public MultiAlignmentHelper setSubjectLeftTitle(String subjectLeftTitle) {
        this.subjectLeftTitle = subjectLeftTitle;
        return this;
    }

    public MultiAlignmentHelper addSubjectQuality(String title, SequenceQuality quality) {
        char[] chars = new char[size()];
        for (int i = 0; i < size(); ++i)
            chars[i] = subjectPositions[i] < 0 ? ' ' : simplifiedQuality(quality.value(subjectPositions[i]));
        addAnnotationString(title, new String(chars));
        return this;
    }

    private static char simplifiedQuality(int value) {
        value /= 5;
        if (value > 9)
            value = 9;
        return Integer.toString(value).charAt(0);
    }

    public MultiAlignmentHelper setSubjectRightTitle(String subjectRightTitle) {
        this.subjectRightTitle = subjectRightTitle;
        return this;
    }

    public MultiAlignmentHelper addAnnotationString(String title, String string) {
        if (string.length() != size())
            throw new IllegalArgumentException();
        annotationStrings.add(string);
        annotationStringTitles.add(title);
        return this;
    }

    public MultiAlignmentHelper setQueryLeftTitle(int id, String queryLeftTitle) {
        this.queryLeftTitles[id] = queryLeftTitle;
        return this;
    }

    public MultiAlignmentHelper setQueryRightTitle(int id, String queryRightTitle) {
        this.queryRightTitles[id] = queryRightTitle;
        return this;
    }

    public int getSubjectPositionAt(int position) {
        return subjectPositions[position];
    }

    public int subjectToAlignmentPosition(int subjectPosition) {
        for (int i = 0; i < subjectPositions.length; i++)
            if (subjectPositions[i] == subjectPosition)
                return i;
        return -1;
    }

    public int getQueryPositionAt(int index, int position) {
        return queryPositions[index][position];
    }

    public int getAbsSubjectPositionAt(int position) {
        return aabs(subjectPositions[position]);
    }

    public int getAbsQueryPositionAt(int index, int position) {
        return aabs(queryPositions[index][position]);
    }

    private static int aabs(int pos) {
        if (pos >= 0)
            return pos;
        if (pos == -1)
            return -1;
        return -2 - pos;
    }

    public int getSubjectFrom() {
        return getFirstPosition(subjectPositions);
    }

    public int getSubjectTo() {
        return getLastPosition(subjectPositions);
    }

    public int getSubjectLength() {
        return 1 + getSubjectTo() - getSubjectFrom();
    }

    public int getQueryFrom(int index) {
        return getFirstPosition(queryPositions[index]);
    }

    public int getQueryTo(int index) {
        return getLastPosition(queryPositions[index]);
    }

    public String getAnnotationString(int i) {
        return annotationStrings.get(i);
    }

    public int size() {
        return subject.length();
    }

    public MultiAlignmentHelper getRange(int from, int to) {
        boolean[] queriesToExclude = new boolean[queries.length];
        int queriesCount = 0;
        for (int i = 0; i < queries.length; i++) {
            boolean exclude = true;
            for (int j = from; j < to; j++)
                if (queryPositions[i][j] != -1) {
                    exclude = false;
                    break;
                }
            queriesToExclude[i] = exclude;
            if (!exclude)
                queriesCount++;
        }

        String[] cQueries = new String[queriesCount];
        int[][] cQueryPositions = new int[queriesCount][];
        BitArray[] cMatch = new BitArray[queriesCount];
        String[] cQueryLeftTitles = new String[queriesCount];
        String[] cQueryRightTitles = new String[queriesCount];

        int j = 0;
        for (int i = 0; i < queries.length; i++) {
            if (queriesToExclude[i])
                continue;
            cQueries[j] = queries[i].substring(from, to);
            cQueryPositions[j] = Arrays.copyOfRange(queryPositions[i], from, to);
            cMatch[j] = match[i].getRange(from, to);
            cQueryLeftTitles[j] = queryLeftTitles[i];
            cQueryRightTitles[j] = queryRightTitles[i];
            j++;
        }

        MultiAlignmentHelper result = new MultiAlignmentHelper(subject.substring(from, to), cQueries,
                Arrays.copyOfRange(subjectPositions, from, to), cQueryPositions, cMatch,
                subjectLeftTitle, cQueryLeftTitles, subjectRightTitle, cQueryRightTitles);

        for (int i = 0; i < annotationStrings.size(); i++)
            result.addAnnotationString(annotationStringTitles.get(i),
                    annotationStrings.get(i).substring(from, to));
        return result;
    }

    public MultiAlignmentHelper[] split(int length) {
        return split(length, false);
    }

    public MultiAlignmentHelper[] split(int length, boolean eqPositionWidth) {
        MultiAlignmentHelper[] ret = new MultiAlignmentHelper[(size() + length - 1) / length];
        for (int i = 0; i < ret.length; ++i) {
            int pointer = i * length;
            int l = Math.min(length, size() - pointer);
            ret[i] = getRange(pointer, pointer + l);
        }
        if (eqPositionWidth)
            alignPositions(ret);
        return ret;
    }

    private static int getFirstPosition(int[] array) {
        for (int pos : array)
            if (pos >= 0)
                return pos;
        for (int pos : array)
            if (pos < -1)
                return -2 - pos;
        return -1;
    }

    private static int getLastPosition(int[] array) {
        for (int i = array.length - 1; i >= 0; i--)
            if (array[i] >= 0)
                return array[i];
        for (int i = array.length - 1; i >= 0; i--)
            if (array[i] < -1)
                return -2 - array[i];
        return -1;
    }

    private static int fixedWidthL(String[] strings) {
        return fixedWidthL(strings, 0);
    }

    private static int fixedWidthL(String[] strings, int minWidth) {
        int length = 0;
        for (String string : strings)
            length = Math.max(length, string.length());
        length = Math.max(length, minWidth);
        for (int i = 0; i < strings.length; i++)
            strings[i] = spaces(length - strings[i].length()) + strings[i];
        return length;
    }

    private static int fixedWidthR(String[] strings) {
        return fixedWidthR(strings, 0);
    }

    private static int fixedWidthR(String[] strings, int minWidth) {
        int length = 0;
        for (String string : strings)
            length = Math.max(length, string.length());
        length = Math.max(length, minWidth);
        for (int i = 0; i < strings.length; i++)
            strings[i] = strings[i] + spaces(length - strings[i].length());
        return length;
    }

    public static class Settings {
        public final boolean markMatchWithSpecialLetter;
        public final boolean lowerCaseMatch;
        public final boolean lowerCaseMismatch;
        public final char specialMatchChar;
        public final char outOfRangeChar;

        public Settings(boolean markMatchWithSpecialLetter, boolean lowerCaseMatch, boolean lowerCaseMismatch,
                        char specialMatchChar, char outOfRangeChar) {
            this.markMatchWithSpecialLetter = markMatchWithSpecialLetter;
            this.lowerCaseMatch = lowerCaseMatch;
            this.lowerCaseMismatch = lowerCaseMismatch;
            this.specialMatchChar = specialMatchChar;
            this.outOfRangeChar = outOfRangeChar;
        }
    }

    @Override
    public String toString() {
        int aCount = queries.length;
        int asSize = annotationStringTitles.size();

        String[] lines = new String[aCount + 1 + asSize];

        for (int i = 0; i < asSize; i++)
            lines[i] = "";

        lines[asSize] = "" + getSubjectFrom();
        for (int i = 0; i < aCount; i++)
            lines[i + 1 + asSize] = "" + getQueryFrom(i);

        int width = fixedWidthL(lines, minimalPositionWidth);

        for (int i = 0; i < asSize; i++)
            lines[i] = annotationStringTitles.get(i) + spaces(width + 1);

        lines[asSize] = (subjectLeftTitle == null ? "" : subjectLeftTitle) +
                " " + lines[asSize];

        for (int i = 0; i < aCount; i++)
            lines[i + 1 + asSize] = (queryLeftTitles[i] == null ? "" : queryLeftTitles[i]) +
                    " " + lines[i + 1 + asSize];

        width = fixedWidthL(lines);

        for (int i = 0; i < asSize; i++)
            lines[i] += " " + annotationStrings.get(i);
        lines[asSize] += " " + subject + " " + getSubjectTo();
        for (int i = 0; i < aCount; i++)
            lines[i + 1 + asSize] += " " + queries[i] + " " + getQueryTo(i);

        width = fixedWidthR(lines);

        lines[asSize] += " " + subjectRightTitle;
        for (int i = 0; i < aCount; i++)
            if (queryRightTitles[i] != null)
                lines[i + 1 + asSize] += " " + queryRightTitles[i];

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i != 0)
                result.append("\n");
            result.append(lines[i]);
        }
        return result.toString();
    }

    public static final Settings DEFAULT_SETTINGS = new Settings(false, true, false, ' ', ' ');
    public static final Settings DOT_MATCH_SETTINGS = new Settings(true, true, false, '.', ' ');

    public static <S extends Sequence<S>> MultiAlignmentHelper build(Settings settings, Range subjectRange,
                                                                     Alignment<S>... alignments) {
        S subject = alignments[0].getSequence1();
        return build(settings, subjectRange, subject, alignments);
    }

    public static <S extends Sequence<S>> MultiAlignmentHelper build(Settings settings, Range subjectRange,
                                                                     S subject, Alignment<S>... alignments) {
        for (Alignment<S> alignment : alignments)
            if (!alignment.getSequence1().equals(subject))
                throw new IllegalArgumentException();

        int subjectPointer = subjectRange.getFrom();
        int subjectPointerTo = subjectRange.getTo();

        int aCount = alignments.length;
        int[] queryPointers = new int[aCount];
        int[] mutationPointers = new int[aCount];
        Mutations<S>[] mutations = new Mutations[aCount];
        List<Boolean>[] matches = new List[aCount];

        IntArrayList subjectPositions = new IntArrayList();
        IntArrayList[] queryPositions = new IntArrayList[aCount];

        StringBuilder subjectString = new StringBuilder();
        StringBuilder[] queryStrings = new StringBuilder[aCount];

        for (int i = 0; i < aCount; i++) {
            queryPointers[i] = alignments[i].getSequence2Range().getFrom();
            matches[i] = new ArrayList<>();
            mutations[i] = alignments[i].getAbsoluteMutations();
            queryPositions[i] = new IntArrayList();
            queryStrings[i] = new StringBuilder();
        }

        final Alphabet<S> alphabet = subject.getAlphabet();

        BitArray processed = new BitArray(aCount);
        while (true) {
            // Checking continue condition
            boolean doContinue = subjectPointer < subjectPointerTo;
            for (int i = 0; i < aCount; i++)
                doContinue |= mutationPointers[i] < mutations[i].size();
            if (!doContinue)
                break;

            processed.clearAll();

            // Processing out of range sequences
            for (int i = 0; i < aCount; i++)
                if (!alignments[i].getSequence1Range().contains(subjectPointer)
                        && !(alignments[i].getSequence1Range().containsBoundary(subjectPointer) &&
                        mutationPointers[i] != mutations[i].size())) {
                    queryStrings[i].append(settings.outOfRangeChar);
                    queryPositions[i].add(-1);
                    matches[i].add(false);
                    processed.set(i);
                }

            // Checking for insertions
            boolean insertion = false;
            for (int i = 0; i < aCount; i++)
                if (mutationPointers[i] < mutations[i].size() &&
                        mutations[i].getTypeByIndex(mutationPointers[i]) == MutationType.Insertion &&
                        mutations[i].getPositionByIndex(mutationPointers[i]) == subjectPointer) {
                    insertion = true;
                    queryStrings[i].append(mutations[i].getToAsSymbolByIndex(mutationPointers[i]));
                    queryPositions[i].add(queryPointers[i]++);
                    matches[i].add(false);
                    mutationPointers[i]++;
                    assert !processed.get(i);
                    processed.set(i);
                }

            if (insertion) { // In case on insertion in query sequence
                subjectString.append('-');
                subjectPositions.add(-2 - subjectPointer);

                for (int i = 0; i < aCount; i++) {
                    if (!processed.get(i)) {
                        queryStrings[i].append('-');
                        queryPositions[i].add(-2 - queryPointers[i]);
                        matches[i].add(false);
                    }
                }
            } else { // In other cases
                char subjectSymbol = subject.symbolAt(subjectPointer);
                subjectString.append(subjectSymbol);
                subjectPositions.add(subjectPointer);

                for (int i = 0; i < aCount; i++) {
                    if (processed.get(i))
                        continue;

                    Mutations<S> cMutations = mutations[i];
                    int cMutationPointer = mutationPointers[i];

                    boolean mutated = false;

                    if (cMutationPointer < cMutations.size()) {
                        int mutPosition = cMutations.getPositionByIndex(cMutationPointer);
                        assert mutPosition >= subjectPointer;
                        mutated = mutPosition == subjectPointer;
                    }

                    if (mutated) {
                        switch (cMutations.getRawTypeByIndex(cMutationPointer)) {
                            case RAW_MUTATION_TYPE_SUBSTITUTION:
                                char symbol = cMutations.getToAsSymbolByIndex(cMutationPointer);
                                queryStrings[i].append(settings.lowerCaseMismatch ?
                                        Character.toLowerCase(symbol) :
                                        symbol);
                                queryPositions[i].add(queryPointers[i]++);
                                matches[i].add(false);
                                break;
                            case RAW_MUTATION_TYPE_DELETION:
                                queryStrings[i].append('-');
                                queryPositions[i].add(-2 - queryPointers[i]);
                                matches[i].add(false);
                                break;
                            default:
                                assert false;
                        }
                        mutationPointers[i]++;
                    } else {
                        if (settings.markMatchWithSpecialLetter)
                            queryStrings[i].append(settings.specialMatchChar);
                        else
                            queryStrings[i].append(settings.lowerCaseMatch ? Character.toLowerCase(subjectSymbol) :
                                    subjectSymbol);
                        queryPositions[i].add(queryPointers[i]++);
                        matches[i].add(true);
                    }
                }
                subjectPointer++;
            }
        }

        int[][] queryPositionsArrays = new int[aCount][];
        BitArray[] matchesBAs = new BitArray[aCount];
        String[] queryStringsArray = new String[aCount];
        for (int i = 0; i < aCount; i++) {
            queryPositionsArrays[i] = queryPositions[i].toArray();
            matchesBAs[i] = new BitArray(matches[i]);
            queryStringsArray[i] = queryStrings[i].toString();
        }

        return new MultiAlignmentHelper(subjectString.toString(), queryStringsArray, subjectPositions.toArray(),
                queryPositionsArrays, matchesBAs);
    }

    public static void alignPositions(MultiAlignmentHelper[] helpers) {
        int maxPositionWidth = 0;
        for (MultiAlignmentHelper helper : helpers)
            maxPositionWidth = Math.max(maxPositionWidth, helper.getActualPositionWidth());
        for (MultiAlignmentHelper helper : helpers)
            helper.setMinimalPositionWidth(maxPositionWidth);
    }

    private static String spaces(int n) {
        char[] c = new char[n];
        Arrays.fill(c, ' ');
        return String.valueOf(c);
    }
}
