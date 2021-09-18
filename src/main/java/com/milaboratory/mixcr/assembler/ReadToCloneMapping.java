/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;


import java.util.Comparator;

public final class ReadToCloneMapping {
    public static final byte CLUSTERED_MASK = 1;
    public static final byte ADDITIONAL_MAPPING_MASK = 1 << 1;
    public static final byte DROPPED_WITH_CLONE_MASK = 1 << 2;
    public static final byte PRE_CLUSTERED_MASK = 1 << 3;
    public static final byte DROPPED_MASK = 1 << 4;
    final long alignmentsId;
    final int cloneIndex;
    final byte mappingType;

    public ReadToCloneMapping(long alignmentsId, int cloneIndex, boolean clustered, boolean additionalMapping, boolean droppedWithClone, boolean preClustered) {
        if (cloneIndex < 0)
            cloneIndex = -1;
        this.alignmentsId = alignmentsId;
        this.cloneIndex = cloneIndex;
        assert !droppedWithClone || cloneIndex < 0;
        this.mappingType = (byte) ((clustered ? CLUSTERED_MASK : 0)
                | (additionalMapping ? ADDITIONAL_MAPPING_MASK : 0)
                | (droppedWithClone ? DROPPED_WITH_CLONE_MASK : 0)
                | (preClustered ? PRE_CLUSTERED_MASK : 0)
                | (cloneIndex < 0 ? DROPPED_MASK : 0));
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
        return (mappingType & CLUSTERED_MASK) != 0;
    }

    public static boolean isMapped(byte mappingType) {
        return (mappingType & ADDITIONAL_MAPPING_MASK) != 0;
    }

    public static boolean isDroppedWithClone(byte mappingType) {
        return (mappingType & DROPPED_WITH_CLONE_MASK) != 0;
    }

    public static boolean isPreClustered(byte mappingType) {
        return (mappingType & PRE_CLUSTERED_MASK) != 0;
    }

    public static boolean isDropped(byte mappingType) {
        return (mappingType & DROPPED_MASK) != 0;
    }

    public static boolean isCorrect(byte mappingType) {
        return (mappingType & 0xE0) == 0
                && (!isDropped(mappingType)
                || mappingType == DROPPED_MASK
                || mappingType == (DROPPED_WITH_CLONE_MASK | DROPPED_MASK)
                || mappingType == (DROPPED_WITH_CLONE_MASK | PRE_CLUSTERED_MASK | DROPPED_MASK));
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
