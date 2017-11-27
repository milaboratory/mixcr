package com.milaboratory.mixcr.basictypes;

import com.milaboratory.util.ArraysUtils;

import static java.lang.Math.abs;
import static java.lang.Math.max;

public interface SequenceHistory {
    /**
     * Length of target
     */
    int length();

    /**
     * Shift read index by specified value (used in .vdjca files merging)
     */
    SequenceHistory shiftReadId(long shift);

    /**
     * Return all raw read ids (sorted) occurring in the history
     */
    long[] readIds();

    /**
     * Return minimal raw read id occurring in the history
     */
    long minReadId();

    /**
     * Initial event, starting point of the history (single fastq record read from file)
     */
    final class RawSequence implements SequenceHistory {
        /**
         * Read index in the initial .fastq file
         */
        public final long readId;
        /**
         * Read index in pair
         */
        public final byte mateIndex;
        /**
         * Read length
         */
        public final int length;
        /**
         * Is reverse complement
         */
        public final boolean isReverseComplement;

        public RawSequence(long readId, byte mateIndex, int length, boolean isReverseComplement) {
            this.readId = readId;
            this.mateIndex = mateIndex;
            this.length = length;
            this.isReverseComplement = isReverseComplement;
        }

        @Override
        public int length() { return length; }

        @Override
        public SequenceHistory shiftReadId(long shift) {
            return new RawSequence(readId + shift, mateIndex, length, isReverseComplement);
        }

        @Override
        public long[] readIds() {
            return new long[]{readId};
        }

        @Override
        public long minReadId() {
            return readId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RawSequence that = (RawSequence) o;

            if (readId != that.readId) return false;
            if (mateIndex != that.mateIndex) return false;
            if (length != that.length) return false;
            return isReverseComplement == that.isReverseComplement;
        }

        @Override
        public int hashCode() {
            int result = (int) (readId ^ (readId >>> 32));
            result = 31 * result + (int) mateIndex;
            result = 31 * result + length;
            result = 31 * result + (isReverseComplement ? 1 : 0);
            return result;
        }
    }

    /**
     * Parent for all events when two alignments or reads are merged into a single one
     */
    abstract class Merge implements SequenceHistory {
        /**
         * Histories of merged alignments
         */
        public final SequenceHistory left, right;
        /**
         * Position of the first nucleotide of the right target in the left target
         */
        public final int offset;
        /**
         * Number of mismatches
         */
        public final int nMismatches;

        Merge(SequenceHistory left, SequenceHistory right, int offset, int nMismatches) {
            this.left = left;
            this.right = right;
            this.offset = offset;
            this.nMismatches = nMismatches;
        }

        @Override
        public int length() {
            return abs(offset) +
                    (offset >= 0 ?
                            max(left.length() - offset, right.length()) :
                            max(left.length(), right.length() + offset) // offset is negative here
                    );
        }

        @Override
        public long[] readIds() {
            return ArraysUtils.getSortedDistinct(ArraysUtils.concatenate(left.readIds(), right.readIds()));
        }

        @Override
        public long minReadId() {
            return Math.min(left.minReadId(), right.minReadId());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Merge merge = (Merge) o;

            if (offset != merge.offset) return false;
            if (nMismatches != merge.nMismatches) return false;
            if (!left.equals(merge.left)) return false;
            return right.equals(merge.right);
        }

        @Override
        public int hashCode() {
            int result = left.hashCode();
            result = 31 * result + right.hashCode();
            result = 31 * result + offset;
            result = 31 * result + nMismatches;
            return result;
        }
    }

    final class SequenceOverlap extends Merge {
        public SequenceOverlap(SequenceHistory left, SequenceHistory right, int offset, int nMismatches) {
            super(left, right, offset, nMismatches);
        }

        @Override
        public SequenceOverlap shiftReadId(long shift) {
            return new SequenceOverlap(left.shiftReadId(shift), right.shiftReadId(shift), offset, nMismatches);
        }
    }

    final class AlignmentOverlap extends Merge {
        public AlignmentOverlap(SequenceHistory left, SequenceHistory right, int offset, int nMismatches) {
            super(left, right, offset, nMismatches);
        }

        @Override
        public AlignmentOverlap shiftReadId(long shift) {
            return new AlignmentOverlap(left.shiftReadId(shift), right.shiftReadId(shift), offset, nMismatches);
        }
    }
}
