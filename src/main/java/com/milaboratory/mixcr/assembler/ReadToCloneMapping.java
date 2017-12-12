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


import com.milaboratory.primitivio.annotations.Serializable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

@Serializable(by = IO.ReadToCloneMappingSerializer.class)
public final class ReadToCloneMapping {
    public static final int MINIMAL_RECORD_SIZE = 8 + 8 + 4 + 1;

    final long alignmentsId;
    final long[] readIds;
    final int cloneIndex;
    final byte mappingType;

    ReadToCloneMapping(long alignmentsId, long[] readIds, int cloneIndex, byte mappingType) {
        this.alignmentsId = alignmentsId;
        this.readIds = readIds;
        this.cloneIndex = cloneIndex;
        this.mappingType = mappingType;
    }

    public ReadToCloneMapping(long alignmentsId, long[] readIds, int cloneIndex, boolean clustered, boolean additionalMapping, boolean droppedWithClone, boolean preClustered) {
        this.alignmentsId = alignmentsId;
        this.readIds = readIds;
        this.cloneIndex = cloneIndex;
        assert !droppedWithClone || cloneIndex < 0;
        this.mappingType = (byte) ((clustered ? 1 : 0) | (additionalMapping ? 2 : 0) | (droppedWithClone ? 4 : 0) | (preClustered ? 8 : 0));
    }

    public int getCloneIndex() {
        return cloneIndex;
    }

    public long getAlignmentsId() {
        return alignmentsId;
    }

    public long[] getReadIds() {
        return readIds;
    }

    public boolean isClustered() {
        return (mappingType & 1) == 1;
    }

    public boolean isMapped() {
        return (mappingType & 2) == 2;
    }

    public boolean isDroppedWithClone() {
        return (mappingType & 4) == 4;
    }

    public boolean isDropped() {
        return cloneIndex < 0;
    }

    public boolean isPreClustered() {
        return (mappingType & 8) == 8;
    }

    public MappingType getMappingType() {
        if (isDroppedWithClone()) return MappingType.DroppedWithClone;
        else if (isDropped()) return MappingType.Dropped;
        else if (isMapped()) return MappingType.Mapped;
        else if (isClustered()) return MappingType.Clustered;
        else if (isPreClustered()) return MappingType.PreClustered;
        else return MappingType.Core;
    }

    @Override
    public String toString() {
        return isDropped() ? "" : "" + alignmentsId + " -> " + cloneIndex + " " + (isPreClustered() ? "p" : "") + (isClustered() ? "c" : "") + (isMapped() ? "m" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReadToCloneMapping that = (ReadToCloneMapping) o;

        if (alignmentsId != that.alignmentsId) return false;
        if (cloneIndex != that.cloneIndex) return false;
        if (mappingType != that.mappingType) return false;
        return Arrays.equals(readIds, that.readIds);
    }

    @Override
    public int hashCode() {
        int result = (int) (alignmentsId ^ (alignmentsId >>> 32));
        result = 31 * result + Arrays.hashCode(readIds);
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

    public static void write(DataOutput output, ReadToCloneMapping object) {
        try {
            output.writeLong(object.alignmentsId);
            output.writeInt(object.readIds.length);
            for (int i = 0; i < object.readIds.length; i++)
                output.writeLong(object.readIds[i]);
            output.writeInt(object.cloneIndex);
            output.writeByte(object.mappingType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ReadToCloneMapping read(DataInput input) {
        try {
            long alignmentsIndex = input.readLong();
            int nReadIds = input.readInt();
            long[] readIds = new long[nReadIds];
            for (int i = 0; i < nReadIds; i++)
                readIds[i] = input.readLong();
            int cloneIndex = input.readInt();
            byte mappingType = input.readByte();
            return new ReadToCloneMapping(alignmentsIndex, readIds, cloneIndex, mappingType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ReadToCloneMapping read(ByteBuffer input) {
        long alignmentsIndex = input.getLong();
        int nReadIds = input.getInt();
        long[] readIds = new long[nReadIds];
        for (int i = 0; i < nReadIds; i++)
            readIds[i] = input.getLong();
        int cloneIndex = input.getInt();
        byte mappingType = input.get();
        return new ReadToCloneMapping(alignmentsIndex, readIds, cloneIndex, mappingType);
    }

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
