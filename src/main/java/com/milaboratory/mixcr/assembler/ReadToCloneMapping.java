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

import java.util.Comparator;

@Serializable(by = IO.ReadToCloneMappingSerializer.class)
public final class ReadToCloneMapping {
    final long alignmentsId;
    final long readId;
    final int cloneIndex;
    final byte mappingType;

    ReadToCloneMapping(long alignmentsId, long readId, int cloneIndex, byte mappingType) {
        this.alignmentsId = alignmentsId;
        this.readId = readId;
        this.cloneIndex = cloneIndex;
        this.mappingType = mappingType;
    }


    public ReadToCloneMapping(long alignmentsId, long readId, int cloneIndex, boolean clustered, boolean additionalMapping) {
        this.alignmentsId = alignmentsId;
        this.readId = readId;
        this.cloneIndex = cloneIndex;
        this.mappingType = (byte) ((clustered ? 1 : 0) | (additionalMapping ? 2 : 0));
    }

    public int getCloneIndex() {
        return cloneIndex;
    }

    public long getAlignmentsId() {
        return alignmentsId;
    }

    public long getReadId() {
        return readId;
    }

    public boolean isClustered() {
        return (mappingType & 1) == 1;
    }

    public boolean isMapped() {
        return (mappingType & 2) == 2;
    }

    public boolean isDropped() {
        return cloneIndex < 0;
    }

    @Override
    public String toString() {
        return "" + alignmentsId + " -> " + cloneIndex + " " + (isClustered() ? "c" : "") + (isMapped() ? "m" : "");
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
            int c = Integer.compare(o1.cloneIndex, o2.cloneIndex);
            return c == 0 ? Long.compare(o1.alignmentsId, o2.alignmentsId) : c;
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
            int c = Long.compare(o1.alignmentsId, o2.alignmentsId);
            return c == 0 ? Integer.compare(o1.cloneIndex, o2.cloneIndex) : c;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AlignmentsComparator;
        }
    }
}
