package com.milaboratory.mixcr.basictypes;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.core.Target;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.ArraysUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static java.lang.Math.max;
import static java.lang.Math.min;

@Serializable(by = SequenceHistory.SequenceHistorySerializer.class)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SequenceHistory.RawSequence.class, name = "RawRead"),
        @JsonSubTypes.Type(value = SequenceHistory.Merge.class, name = "Merged"),
        @JsonSubTypes.Type(value = SequenceHistory.Extend.class, name = "Extended")
})
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
     * Returns hierarchically ordered list of raw reads of this assembly
     */
    List<RawSequence> rawReads();

    /**
     * Calculates read offset inside the assembly
     *
     * @param readIndex target read index
     * @return offset value (position of first nucleotide of the read inside this assembly), or null if there is no
     * such read inside the assembly
     */
    Integer offset(FullReadIndex readIndex);

    /**
     * Return minimal raw read id occurring in the history
     */
    long minReadId();

    /**
     * Returns compact string representation of this assembly
     */
    String compactString();

    final class FullReadIndex {
        /**
         * Read index in the initial .fastq file
         */
        public final long readId;
        /**
         * Read index in pair (0 or 1)
         */
        public final byte mateIndex;
        /**
         * Is reverse complement
         */
        public final boolean isReverseComplement;

        public FullReadIndex(@JsonProperty("readId") long readId,
                             @JsonProperty("mateIndex") byte mateIndex,
                             @JsonProperty("isReverseComplement") boolean isReverseComplement) {
            this.readId = readId;
            this.mateIndex = mateIndex;
            this.isReverseComplement = isReverseComplement;
        }

        /**
         * Returns full read index object with newIndex.readId == this.readId + shift
         */
        public FullReadIndex shiftReadId(long shift) {
            return new FullReadIndex(readId + shift, mateIndex, isReverseComplement);
        }

        @Override
        public String toString() {
            return (isReverseComplement ? "-" : "") +
                    readId + "R" + mateIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FullReadIndex)) return false;

            FullReadIndex that = (FullReadIndex) o;

            if (readId != that.readId) return false;
            if (mateIndex != that.mateIndex) return false;
            return isReverseComplement == that.isReverseComplement;
        }

        @Override
        public int hashCode() {
            int result = (int) (readId ^ (readId >>> 32));
            result = 31 * result + (int) mateIndex;
            result = 31 * result + (isReverseComplement ? 1 : 0);
            return result;
        }
    }

    /**
     * Initial event, starting point of the history (single fastq record read from file)
     */
    final class RawSequence implements SequenceHistory {
        /**
         * Full read index
         */
        @JsonUnwrapped
        @JsonProperty(access = READ_ONLY)
        public final FullReadIndex index;

        /**
         * Read length
         */
        public final int length;

        private RawSequence(FullReadIndex index, int length) {
            this.index = index;
            this.length = length;
        }

        @JsonCreator
        public RawSequence(@JsonProperty("readId") long readId,
                           @JsonProperty("mateIndex") byte mateIndex,
                           @JsonProperty("isReverseComplement") boolean isReverseComplement,
                           @JsonProperty("length") int length) {
            this(new FullReadIndex(readId, mateIndex, isReverseComplement), length);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public SequenceHistory shiftReadId(long shift) {
            return new RawSequence(index.shiftReadId(shift), length);
        }

        @Override
        public long[] readIds() {
            return new long[]{index.readId};
        }

        @Override
        public List<RawSequence> rawReads() {
            return Collections.singletonList(this);
        }

        @Override
        public long minReadId() {
            return index.readId;
        }

        @Override
        public Integer offset(FullReadIndex readIndex) {
            if (this.index.equals(readIndex))
                return 0;
            else
                return null;
        }

        @Override
        public String compactString() {
            return index.toString();
        }

        @Override
        public String toString() {
            return compactString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RawSequence)) return false;

            RawSequence that = (RawSequence) o;

            if (length != that.length) return false;
            return index.equals(that.index);
        }

        @Override
        public int hashCode() {
            int result = index.hashCode();
            result = 31 * result + length;
            return result;
        }

        public static RawSequence[] of(long readId, Target target) {
            RawSequence[] rw = new RawSequence[target.numberOfParts()];
            for (int i = 0; i < rw.length; i++)
                rw[i] = new RawSequence(readId,
                        (byte) target.getReadIdOfTarget(i),
                        target.getRCStateOfTarget(i),
                        target.targets[i].size());
            return rw;
        }
    }

    enum OverlapType {
        SequenceOverlap("Seq"),
        AlignmentOverlap("Aln"),
        CDR3Overlap("CDR3"),
        ExtensionMerge("Ext");

        public final String stringRepresentation;

        OverlapType(String stringRepresentation) {
            this.stringRepresentation = stringRepresentation;
        }
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

        @JsonCreator
        public Merge(
                @JsonProperty("overlapType") OverlapType overlapType,
                @JsonProperty("left") SequenceHistory left,
                @JsonProperty("right") SequenceHistory right,
                @JsonProperty("offset") int offset,
                @JsonProperty("nMismatches") int nMismatches) {
            if (left == null || right == null)
                throw new NullPointerException();
            this.overlapType = overlapType;
            this.left = left;
            this.right = right;
            this.offset = offset;
            this.nMismatches = nMismatches;
        }

        @Override
        public int length() {
            return offset >= 0 ?
                    max(left.length(), right.length() + offset) :
                    max(left.length() - offset, right.length()); // offset is negative here
        }

        public int overlap() {
            return offset >= 0 ?
                    min(left.length() - offset, right.length()) :
                    min(left.length(), right.length() + offset); // offset is negative here
        }

        @Override
        public long[] readIds() {
            return ArraysUtils.getSortedDistinct(ArraysUtils.concatenate(left.readIds(), right.readIds()));
        }

        @Override
        public List<RawSequence> rawReads() {
            ArrayList<RawSequence> res = new ArrayList<>();
            res.addAll(left.rawReads());
            res.addAll(right.rawReads());
            return res;
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
        public Integer offset(FullReadIndex readIndex) {
            Integer leftOffset = left.offset(readIndex);
            if (leftOffset != null)
                return offset >= 0 ?
                        leftOffset :
                        leftOffset - offset;

            Integer rightOffset = right.offset(readIndex);
            if (rightOffset != null)
                return offset >= 0 ?
                        rightOffset + offset :
                        rightOffset;

            return null;
        }

        @Override
        public String compactString() {
            return offset >= 0 ?
                    "(" + left.compactString() + "=>[" + overlapType.stringRepresentation + ":" + overlap() +
                            ":" + nMismatches + "]<=" + right.compactString() + ")" :
                    "(" + right.compactString() + "=>[" + overlapType.stringRepresentation + ":" + overlap() +
                            ":" + nMismatches + "]<=" + left.compactString() + ")";
        }

        @Override
        public String toString() {
            return compactString();
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

        @JsonCreator
        public Extend(
                @JsonProperty("original") SequenceHistory original,
                @JsonProperty("extensionLeft") int extensionLeft,
                @JsonProperty("extensionRight") int extensionRight) {
            if (original == null)
                throw new NullPointerException();
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
        public List<RawSequence> rawReads() {
            return original.rawReads();
        }

        @Override
        public Integer offset(FullReadIndex readIndex) {
            Integer innerOffset = original.offset(readIndex);
            return innerOffset == null ?
                    null :
                    extensionLeft + innerOffset;
        }

        @Override
        public String compactString() {
            return "(" +
                    (extensionLeft != 0 ? (extensionLeft + "<=") : "") +
                    original.compactString() +
                    (extensionRight != 0 ? ("=>" + extensionRight) : "") +
                    ")";
        }

        @Override
        public String toString() {
            return compactString();
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
                output.writeByte((obj.index.isReverseComplement ? (byte) 0x80 : (byte) 0x00) | obj.index.mateIndex);
                // Target length
                output.writeVarInt(obj.length);
                // Read id
                output.writeVarLong(obj.index.readId);
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
                return new RawSequence(readId, (byte) (rcAndMateIndex & 0x7F),
                        (rcAndMateIndex & 0x80) == 0x80, targetLength);
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
