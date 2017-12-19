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
package com.milaboratory.mixcr.assembler;


import java.util.Comparator;

public final class ReadToCloneMapping {
    public static final byte DROPPED = 16;
    final long alignmentsId;
    final int cloneIndex;
    final byte mappingType;

    public ReadToCloneMapping(long alignmentsId, int cloneIndex, boolean clustered, boolean additionalMapping, boolean droppedWithClone, boolean preClustered) {
        if (cloneIndex < 0)
            cloneIndex = -1;
        this.alignmentsId = alignmentsId;
        this.cloneIndex = cloneIndex;
        assert !droppedWithClone || cloneIndex < 0;
        this.mappingType = (byte) ((clustered ? 1 : 0)
                | (additionalMapping ? 2 : 0)
                | (droppedWithClone ? 4 : 0)
                | (preClustered ? 8 : 0)
                | (cloneIndex < 0 ? 16 : 0));
    }

    public int getCloneIndex() {
        return cloneIndex;
    }

    public long getAlignmentsId() {
        return alignmentsId;
    }

    public boolean isClustered() {
        return isClustered(mappingType);
    }

    public boolean isMapped() {
        return isMapped(mappingType);
    }

    public boolean isDroppedWithClone() {
        return isDroppedWithClone(mappingType);
    }

    public boolean isDropped() {
        return isDropped(mappingType);
    }

    public boolean isPreClustered() {
        return isPreClustered(mappingType);
    }

    public MappingType getMappingType() {
        return getMappingType(mappingType);
    }

    public byte getMappingTypeByte() {
        return mappingType;
    }

    @Override
    public String toString() {
        return isDropped() ? "" : "" + alignmentsId + " -> " + cloneIndex + " " + (isPreClustered() ? "p" : "") + (isClustered() ? "c" : "") + (isMapped() ? "m" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadToCloneMapping)) return false;

        ReadToCloneMapping that = (ReadToCloneMapping) o;

        if (alignmentsId != that.alignmentsId) return false;
        if (cloneIndex != that.cloneIndex) return false;
        return mappingType == that.mappingType;
    }

    @Override
    public int hashCode() {
        int result = (int) (alignmentsId ^ (alignmentsId >>> 32));
        result = 31 * result + cloneIndex;
        result = 31 * result + (int) mappingType;
        return result;
    }

    public enum MappingType {
        Core, Clustered, Mapped, Dropped, DroppedWithClone, PreClustered;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public static boolean isClustered(byte mappingType) {
        return (mappingType & 1) == 1;
    }

    public static boolean isMapped(byte mappingType) {
        return (mappingType & 2) == 2;
    }

    public static boolean isDroppedWithClone(byte mappingType) {
        return (mappingType & 4) == 4;
    }

    public static boolean isPreClustered(byte mappingType) {
        return (mappingType & 8) == 8;
    }

    public static boolean isDropped(byte mappingType) {
        return (mappingType & 16) == 16;
    }

    public static boolean isCorrect(byte mappingType) {
        return (mappingType & 0xE0) == 0
                && (!isDropped(mappingType)
                || mappingType == DROPPED);
    }

    public static MappingType getMappingType(byte mappingType) {
        if (isDroppedWithClone(mappingType)) return MappingType.DroppedWithClone;
        else if (isDropped(mappingType)) return MappingType.Dropped;
        else if (isMapped(mappingType)) return MappingType.Mapped;
        else if (isClustered(mappingType)) return MappingType.Clustered;
        else if (isPreClustered(mappingType)) return MappingType.PreClustered;
        else return MappingType.Core;
    }

    // public static void write(DataOutput output, ReadToCloneMapping object) {
    //     try {
    //         output.writeLong(object.alignmentsId);
    //         output.writeInt(object.readIds.length);
    //         for (int i = 0; i < object.readIds.length; i++)
    //             output.writeLong(object.readIds[i]);
    //         output.writeInt(object.cloneIndex);
    //         output.writeByte(object.mappingType);
    //     } catch (IOException e) {
    //         throw new RuntimeException(e);
    //     }
    // }
    //
    // public static ReadToCloneMapping read(DataInput input) {
    //     try {
    //         long alignmentsIndex = input.readLong();
    //         int nReadIds = input.readInt();
    //         long[] readIds = new long[nReadIds];
    //         for (int i = 0; i < nReadIds; i++)
    //             readIds[i] = input.readLong();
    //         int cloneIndex = input.readInt();
    //         byte mappingType = input.readByte();
    //         return new ReadToCloneMapping(alignmentsIndex, readIds, cloneIndex, mappingType);
    //     } catch (IOException e) {
    //         throw new RuntimeException(e);
    //     }
    // }
    //
    // public static ReadToCloneMapping read(ByteBuffer input) {
    //     long alignmentsIndex = input.getLong();
    //     int nReadIds = input.getInt();
    //     long[] readIds = new long[nReadIds];
    //     for (int i = 0; i < nReadIds; i++)
    //         readIds[i] = input.getLong();
    //     int cloneIndex = input.getInt();
    //     byte mappingType = input.get();
    //     return new ReadToCloneMapping(alignmentsIndex, readIds, cloneIndex, mappingType);
    // }

    public static final Comparator<ReadToCloneMapping>
            CLONE_COMPARATOR = new CloneComparator(),
            ALIGNMENTS_COMPARATOR = new AlignmentsComparator();

    private static final class CloneComparator implements Comparator<ReadToCloneMapping>,
                                                          java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private CloneComparator() {
        }

        @Override
        public int compare(ReadToCloneMapping o1, ReadToCloneMapping o2) {
            int c;
            if ((c = Integer.compare(o1.cloneIndex, o2.cloneIndex)) != 0)
                return c;
            if ((c = Long.compare(o1.alignmentsId, o2.alignmentsId)) != 0)
                return c;
            return Byte.compare(o1.mappingType, o2.mappingType);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CloneComparator;
        }
    }

    private static final class AlignmentsComparator implements Comparator<ReadToCloneMapping>,
                                                               java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private AlignmentsComparator() {
        }

        @Override
        public int compare(ReadToCloneMapping o1, ReadToCloneMapping o2) {
            int c;
            if ((c = Long.compare(o1.alignmentsId, o2.alignmentsId)) != 0)
                return c;
            if ((c = Integer.compare(o1.cloneIndex, o2.cloneIndex)) != 0)
                return c;
            return Byte.compare(o1.mappingType, o2.mappingType);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AlignmentsComparator;
        }
    }
}
