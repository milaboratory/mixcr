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

public final class ClonalSequence implements Iterable<NSequenceWithQuality> {
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
