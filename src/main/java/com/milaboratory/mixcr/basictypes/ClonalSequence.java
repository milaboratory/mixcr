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

import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.MutationType;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequencesUtils;
import com.milaboratory.util.ArrayIterator;

import java.util.Iterator;

import static com.milaboratory.core.mutations.Mutation.*;

public final class ClonalSequence implements Iterable<NSequenceWithQuality>, Comparable<ClonalSequence> {
    public final NSequenceWithQuality[] sequences;
    protected final NSequenceWithQuality sequence;
    protected final int[] pointers;
    private Stretch[] stretches;

    public ClonalSequence(final NSequenceWithQuality[] sequences) {
        this.sequences = sequences;
        this.sequence = SequencesUtils.concatenate(sequences);
        this.pointers = new int[sequences.length];
        pointers[0] = sequences[0].size();
        for (int i = 1; i < sequences.length; i++)
            pointers[i] = pointers[i - 1] + sequences[i].size();
    }

    public NSequenceWithQuality getConcatenated() {
        return sequence;
    }

    public NSequenceWithQuality get(int i) {
        return sequences[i];
    }

    public int size() {
        return sequences.length;
    }

    public boolean isCompatible(ClonalSequence other) {
        if (other.sequences.length != sequences.length)
            return false;
        for (int i = 0; i < sequences.length; ++i)
            if (sequences[i].size() != other.sequences[i].size())
                return false;
        return true;
    }

    public Stretch[] getStretches() {
        if (stretches == null) {
            stretches = new Stretch[sequences.length - 1];
            for (int i = 1; i < sequences.length; ++i) {
                NucleotideSequence left = sequences[i - 1].getSequence(),
                        right = sequences[i].getSequence();
                if (left.size() == 0 || right.size() == 0) {
                    stretches[i - 1] = new Stretch(left.size() != 0
                            ? left.codeAt(left.size() - 1)
                            : right.size() != 0
                            ? right.codeAt(0)
                            : 0
                    );
                    continue;
                }
                int leftSize = left.size(), rightSize = right.size();
                byte code = left.codeAt(leftSize - 1);
                if (code != right.codeAt(0)) {
                    stretches[i - 1] = new Stretch(code);
                    continue;
                }
                int leftDelta = 0, rightDelta = 0;
                while (leftDelta < leftSize && left.codeAt(leftSize - leftDelta - 1) == code)
                    ++leftDelta;
                while (rightDelta < rightSize && right.codeAt(rightDelta) == code)
                    ++rightDelta;
                stretches[i - 1] = new Stretch(leftDelta, rightDelta, code);
            }
        }
        return stretches;
    }

    public boolean isCompatible(ClonalSequence other, Mutations<NucleotideSequence> mutations) {
        return isCompatible(0, other, mutations, 0);
    }

    private boolean isCompatible(int index,
                                 ClonalSequence other,
                                 Mutations<NucleotideSequence> mutations,
                                 int additionalDelta) {
        //delta due mutations
        int delta = getLengthDelta(mutations, index == 0 ? 0 : pointers[index - 1],
                index == size() - 1 ? pointers[index] + 1 : pointers[index]);
        //requested delta
        int requestedDelta = sequences[index].size() + delta + additionalDelta - other.sequences[index].size();
        if (requestedDelta == 0)
            if (index == size() - 1)
                return true;
            else
                return isCompatible(index + 1, other, mutations, 0);
        if (index == size() - 1)
            return false;

        if (requestedDelta < 0) {
            requestedDelta = -requestedDelta;
            //we need to "add insertions" to this

            //first, let's check whether there are boundary insertions
            int boundaryInsertions = countOf(mutations, MutationType.Insertion, pointers[index], pointers[index] + 1);
            //if number of boundary insertions greater than we need, then ok.
            if (boundaryInsertions >= requestedDelta)
                return isCompatible(index + 1, other, mutations, -requestedDelta);

            Stretch stretch = getStretches()[index];
            if (stretch.leftDelta == 0)
                return false;
            stretch = truncate(mutations, stretch, pointers[index]);

            //if not, then we try to check insertion with same letter in stretch
            int insertionsInStretch = countOf(mutations, MutationType.Insertion, pointers[index], pointers[index] + stretch.rightDelta);
            if (insertionsInStretch >= requestedDelta)
                return isCompatible(index + 1, other, mutations, -requestedDelta);

            //if not, then we try to find deletions in stretch
            int deletionsInStretch = countOf(mutations, MutationType.Deletion, pointers[index] - stretch.leftDelta, pointers[index]);
            if (2 * deletionsInStretch >= requestedDelta - insertionsInStretch)
                return isCompatible(index + 1, other, mutations,
                        -requestedDelta);
        } else {
            //we need to "remove insertions" from this

            Stretch stretch = getStretches()[index];
            if (stretch.rightDelta == 0)
                return false;
            stretch = truncate(mutations, stretch, pointers[index]);

            //test that there are deletions in stretch
            int deletionsInStretch = countOf(mutations, MutationType.Deletion, pointers[index], pointers[index] + stretch.rightDelta);
            if (deletionsInStretch >= requestedDelta)
                return isCompatible(index + 1, other, mutations, requestedDelta);

            //if no, then we try to check insertion with same letter in stretch

            int insertionsInStretch = countOf(mutations, MutationType.Insertion, pointers[index] - stretch.leftDelta, pointers[index]);
            if (2 * insertionsInStretch >= requestedDelta - deletionsInStretch)
                return isCompatible(index + 1, other, mutations,
                        requestedDelta);
        }
        return false;
    }

    @Override
    public Iterator<NSequenceWithQuality> iterator() {
        return new ArrayIterator<>(sequences);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClonalSequence that = (ClonalSequence) o;

        for (int i = 0; i < sequences.length; ++i)
            if (!that.sequences[i].getSequence().equals(sequences[i].getSequence()))
                return false;

        return true;
    }

    @Override
    public int hashCode() {
        return sequence.getSequence().hashCode();
    }

    @Override
    public int compareTo(ClonalSequence o) {
        return this.sequence.getSequence().compareTo(o.sequence.getSequence());
    }

    private static Stretch truncate(Mutations<NucleotideSequence> mutations, Stretch initial, int boundary) {
        int leftDelta = initial.leftDelta;
        for (int i = 0; i < mutations.size(); ++i) {
            int pos = mutations.getPositionByIndex(i);
            if (pos < boundary - initial.leftDelta)
                continue;
            if (pos >= boundary)
                break;
            if (Mutation.isDeletion(mutations.getMutation(i)))
                continue;
            if (Mutation.getTo(mutations.getMutation(i)) != initial.letter)
                leftDelta = boundary - pos;
        }

        int rightDelta = initial.rightDelta;
        for (int i = mutations.size() - 1; i >= 0; --i) {
            int pos = mutations.getPositionByIndex(i);
            if (pos > boundary + initial.rightDelta)
                continue;
            if (pos < boundary)
                break;
            if (Mutation.isDeletion(mutations.getMutation(i)))
                continue;
            if (Mutation.getTo(mutations.getMutation(i)) != initial.letter)
                rightDelta = pos - boundary + 1;
        }

        return new Stretch(leftDelta, rightDelta, initial.letter);
    }

    private static final class Stretch {
        final int leftDelta, rightDelta;
        final byte letter;

        private Stretch(byte letter) {
            this(0, 0, letter);
        }

        private Stretch(int leftDelta, int rightDelta, byte letter) {
            this.leftDelta = leftDelta;
            this.rightDelta = rightDelta;
            this.letter = letter;
        }
    }

    private static int getLengthDelta(Mutations<NucleotideSequence> mutations, int from, int to) {
        int fromIndex = 0;
        for (; fromIndex < mutations.size() && mutations.getPositionByIndex(fromIndex) < from; ++fromIndex) ;
        if (fromIndex >= mutations.size())
            return 0;
        int toIndex = mutations.size() - 1;
        for (; toIndex >= 0 && mutations.getPositionByIndex(toIndex) >= to; --toIndex) ;

        int delta = 0;
        for (int i = fromIndex; i <= toIndex; ++i)
            switch (mutations.getMutation(i) & MUTATION_TYPE_MASK) {
                case RAW_MUTATION_TYPE_DELETION:
                    --delta;
                    break;
                case RAW_MUTATION_TYPE_INSERTION:
                    ++delta;
                    break;
            }
        return delta;
    }

    private static int countOf(final Mutations<NucleotideSequence> mutations,
                               final MutationType type, int from, int to) {
        int fromIndex = 0;
        for (; fromIndex < mutations.size() && mutations.getPositionByIndex(fromIndex) < from; ++fromIndex) ;
        if (fromIndex >= mutations.size())
            return 0;
        int toIndex = mutations.size() - 1;
        for (; toIndex >= 0 && mutations.getPositionByIndex(toIndex) >= to; --toIndex) ;

        int result = 0;
        for (int i = fromIndex; i <= toIndex; ++i)
            if ((mutations.getMutation(i) & MUTATION_TYPE_MASK) == type.rawType)
                result++;
        return result;
    }

}
