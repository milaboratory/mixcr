package com.milaboratory.mixcr.basictypes;

import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.ArraysUtils;

import static java.lang.Math.abs;
import static java.lang.Math.max;

@Serializable(by = SequenceHistory.SequenceHistorySerializer.class)
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
        public int length() {
            return length;
        }

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

    enum OverlapType {
        SequenceOverlap,
        AlignmentOverlap,
        CDR3Overlap,
        ExtensionMerge
    }

    /**
     * Parent for all events when two alignments or reads are merged into a single one
     */
    final class Merge implements SequenceHistory {
        /**
         * Overlap type
         */
        final OverlapType overlapType;
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

        public Merge(OverlapType overlapType, SequenceHistory left, SequenceHistory right, int offset, int nMismatches) {
            this.overlapType = overlapType;
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
        public Merge shiftReadId(long shift) {
            return new Merge(overlapType, left.shiftReadId(shift), right.shiftReadId(shift), offset, nMismatches);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Merge merge = (Merge) o;

            if (offset != merge.offset) return false;
            if (nMismatches != merge.nMismatches) return false;
            if (overlapType != merge.overlapType) return false;
            if (!left.equals(merge.left)) return false;
            return right.equals(merge.right);
        }

        @Override
        public int hashCode() {
            int result = overlapType.hashCode();
            result = 31 * result + left.hashCode();
            result = 31 * result + right.hashCode();
            result = 31 * result + offset;
            result = 31 * result + nMismatches;
            return result;
        }
    }

    /**
     * Parent for all events when alignment was extended
     */
    final class Extend implements SequenceHistory {
        final SequenceHistory original;
        final int extensionLeft, extensionRight;

        public Extend(SequenceHistory original, int extensionLeft, int extensionRight) {
            this.original = original;
            this.extensionLeft = extensionLeft;
            this.extensionRight = extensionRight;
        }

        @Override
        public int length() {
            return extensionLeft + original.length() + extensionRight;
        }

        @Override
        public SequenceHistory shiftReadId(long shift) {
            return new Extend(original.shiftReadId(shift), extensionLeft, extensionRight);
        }

        @Override
        public long[] readIds() {
            return original.readIds();
        }

        @Override
        public long minReadId() {
            return original.minReadId();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Extend)) return false;

            Extend extend = (Extend) o;

            if (extensionLeft != extend.extensionLeft) return false;
            if (extensionRight != extend.extensionRight) return false;
            return original.equals(extend.original);
        }

        @Override
        public int hashCode() {
            int result = original.hashCode();
            result = 31 * result + extensionLeft;
            result = 31 * result + extensionRight;
            return result;
        }
    }

    final class SequenceHistorySerializer implements Serializer<SequenceHistory> {
        @Override
        public void write(PrimitivO output, SequenceHistory object) {
            if (object instanceof RawSequence) {
                RawSequence obj = (RawSequence) object;
                // Type descriptor
                output.writeByte(1);
                // RC flag and mate index
                output.writeByte((obj.isReverseComplement ? (byte) 0x80 : (byte) 0x00) | obj.mateIndex);
                // Target length
                output.writeVarInt(obj.length);
                // Read id
                output.writeVarLong(obj.readId);
            } else if (object instanceof Merge) {
                Merge obj = (Merge) object;
                // Type descriptor
                output.writeByte(2);
                // Overlap type
                output.writeObject(obj.overlapType);
                // Offset and number of mismatches
                output.writeVarInt(obj.offset);
                output.writeVarInt(obj.nMismatches);
                // Recursive write parent history entries
                write(output, obj.left);
                write(output, obj.right);
            } else if (object instanceof Extend) {
                Extend obj = (Extend) object;
                // Type descriptor
                output.writeByte(3);
                // Left and right extension length
                output.writeVarInt(obj.extensionLeft);
                output.writeVarInt(obj.extensionRight);
                // Writing parent history entry
                write(output, obj.original);
            } else
                throw new IllegalArgumentException("Type not supported: " + object.getClass());
        }

        @Override
        public SequenceHistory read(PrimitivI input) {
            // Reading type descriptor
            byte t = input.readByte();
            if (t == 1) {
                byte rcAndMateIndex = input.readByte();
                int targetLength = input.readVarInt();
                long readId = input.readVarLong();
                return new RawSequence(readId, (byte) (rcAndMateIndex & 0x7F), targetLength,
                        (rcAndMateIndex & 0x80) == 0x80);
            } else if (t == 2) {
                OverlapType type = input.readObject(OverlapType.class);
                int offset = input.readVarInt();
                int nMismatches = input.readVarInt();
                SequenceHistory left = read(input);
                SequenceHistory right = read(input);
                return new Merge(type, left, right, offset, nMismatches);
            } else if (t == 3) {
                int extensionLeft = input.readVarInt();
                int extensionRight = input.readVarInt();
                SequenceHistory original = read(input);
                return new Extend(original, extensionLeft, extensionRight);
            } else {
                throw new RuntimeException("Unknown history entry type.");
            }
        }

        @Override
        public boolean isReference() {
            return false;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }
}
