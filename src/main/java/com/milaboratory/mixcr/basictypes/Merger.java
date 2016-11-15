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

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.mutations.MutationsUtil;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.core.sequence.SequenceQualityBuilder;

class Merger {
    public static final byte MAX_QUALITY = 45;

    public static NSequenceWithQuality merge(Range range,
                                             Alignment<NucleotideSequence>[] alignments,
                                             NSequenceWithQuality[] targets) {
        // Checking arguments
        if (alignments.length != targets.length)
            throw new IllegalArgumentException();

        // Extracting reference sequence
        NucleotideSequence sequence = alignments[0].getSequence1();
        for (int i = 1; i < alignments.length; i++)
            if (sequence != alignments[i].getSequence1())
                throw new IllegalArgumentException("Different reference sequences.");

        int position = range.getFrom();
        int localPosition = 0;

        int[] mPointers = new int[alignments.length];
        for (int i = 0; i < alignments.length; i++)
            mPointers[i] = firstIndexAfter(alignments[i].getAbsoluteMutations(), position);


        // Aggregator of quality information
        SequenceQualityBuilder qualityBuilder = new SequenceQualityBuilder().ensureCapacity(range.length());
        //Aggregator of mutations
        MutationsBuilder<NucleotideSequence> mutationsBuilder =
                new MutationsBuilder<>(NucleotideSequence.ALPHABET);

        do {
            int winnerIndex = -1;
            byte bestQuality = -1;
            int winnerMPointer = -1, winnerMLength = -1;
            for (int i = 0; i < alignments.length; i++) {
                Alignment<NucleotideSequence> al = alignments[i];

                if (!al.getSequence1Range().contains(position))
                    continue;

                // Current mutation index
                int mPointer = mPointers[i];
                Mutations<NucleotideSequence> mutations = al.getAbsoluteMutations();

                // Number of mutations with the same position
                int length;
                if (mPointer < mutations.size() && mutations.getPositionByIndex(mPointer) == position) {
                    length = 1;
                    while (mPointer + length < mutations.size() &&
                            mutations.getPositionByIndex(mPointer + length) == position)
                        ++length;
                } else
                    length = 0;

                byte pointQuality = getQuality(position, mPointer, length, al, targets[i].getQuality());

                if (bestQuality < pointQuality) {
                    winnerIndex = i;
                    winnerMPointer = mPointer;
                    winnerMLength = length;
                    bestQuality = pointQuality;
                }
            }

            Mutations<NucleotideSequence> winnerMutations = alignments[winnerIndex].getAbsoluteMutations();

            byte sumQuality = 0;

            // Second pass to calculate score
            OUT:
            for (int i = 0; i < alignments.length; i++) {
                Alignment<NucleotideSequence> al = alignments[i];

                if (!al.getSequence1Range().contains(position))
                    continue;

                // Current mutation index
                int mPointer = mPointers[i];
                Mutations<NucleotideSequence> mutations = al.getAbsoluteMutations();

                // Number of mutations with the same position
                int length;
                if (mPointer < mutations.size() && mutations.getPositionByIndex(mPointer) == position) {
                    length = 1;
                    while (mPointer + length < mutations.size() &&
                            mutations.getPositionByIndex(mPointer + length) == position)
                        ++length;
                } else
                    length = 0;

                // Advancing pointer
                mPointers[i] += length;

                if (length != winnerMLength)
                    continue;

                for (int k = 0; k < length; ++k)
                    if (mutations.getMutation(mPointer + k) != winnerMutations.getMutation(winnerMPointer + k))
                        continue OUT;

                byte pointQuality = getQuality(position, mPointer, length, al, targets[i].getQuality());

                sumQuality += pointQuality;
            }

            // todo ??
            if (winnerIndex == -1)
                return null;

            sumQuality = (byte) Math.min(sumQuality, MAX_QUALITY);
            qualityBuilder.append(sumQuality);

            if (winnerMLength != 0)
                mutationsBuilder.append(winnerMutations, winnerMPointer, winnerMLength);
            ++localPosition;
        } while (++position < range.getTo());

        Mutations<NucleotideSequence> mutations = mutationsBuilder.createAndDestroy();

        NSequenceWithQuality toMutate = new NSequenceWithQuality(sequence.getRange(range), qualityBuilder.createAndDestroy());

        return MutationsUtil.mutate(toMutate, mutations.move(-range.getFrom()));
    }

    private static byte getQuality(int position, int pointer, int length, Alignment<NucleotideSequence> alignment, SequenceQuality quality) {
        if (length == 0)
            return quality.value(alignment.convertToSeq2Position(position));

        switch (alignment.getAbsoluteMutations().getRawTypeByIndex(pointer)) {
            case Mutation.RAW_MUTATION_TYPE_SUBSTITUTION:
                return quality.value(alignment.convertToSeq2Position(position));
            case Mutation.RAW_MUTATION_TYPE_DELETION:
                int localPosition = -2 - alignment.convertToSeq2Position(position);
                if (quality.size() == localPosition)
                    return quality.value(localPosition - 1);
                else if (localPosition == 0)
                    return quality.value(localPosition);
                else
                    return (byte) ((quality.value(localPosition - 1) + quality.value(localPosition)) / 2);
            case Mutation.RAW_MUTATION_TYPE_INSERTION:
                int from = alignment.convertToSeq2Position(position - 1);

                if (from < 0)
                    if (from == -1)
                        from = alignment.convertToSeq2Position(position) - 1;
                    else
                        from = -from - 2 + 1;
                else
                    from += 1;

                int to = alignment.convertToSeq2Position(position);

                if (to == -1)
                    throw new IllegalArgumentException(); // assert false!!!

                if (to < 0)
                    to = -2 - to;

                int val = 0;
                if (from < 0 || to < 0)
                    throw new IllegalArgumentException();
                for (int i = from; i <= to; ++i)
                    val += quality.value(i);
                return (byte) (val / (to - from + 1));
        }
        return (byte) -1;
    }

    private static int firstIndexAfter(Mutations<NucleotideSequence> muts, int position) {
        for (int i = 0; i < muts.size(); ++i)
            if (muts.getPositionByIndex(i) >= position)
                return i;
        return muts.size();
    }

    public static class MergerResult {
        public final float mergingScore;
        public final Alignment<NucleotideSequence> alignment;
        public final NSequenceWithQuality newTarget;

        public MergerResult(float mergingScore, Alignment<NucleotideSequence> alignment, NSequenceWithQuality newTarget) {
            this.mergingScore = mergingScore;
            this.alignment = alignment;
            this.newTarget = newTarget;
        }
    }
}
