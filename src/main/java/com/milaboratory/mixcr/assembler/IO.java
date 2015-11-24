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

import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import org.mapdb.BTreeKeySerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.util.Comparator;

/**
 * Created by poslavsky on 13/08/14.
 */
public final class IO {
    public static final ReadToCloneMappingMapDBSerializer MAPDB_SERIALIZER = new ReadToCloneMappingMapDBSerializer();

    public static final class ReadToCloneMappingMapDBSerializer implements
            org.mapdb.Serializer<ReadToCloneMapping>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void serialize(DataOutput out, ReadToCloneMapping value) throws IOException {
            write0(out, value);
        }

        @Override
        public ReadToCloneMapping deserialize(DataInput in, int available) throws IOException {
            return read0(in);
        }

        @Override
        public int fixedSize() {
            return 21;
        }
    }

    public static final class ReadToCloneMappingBtreeSerializer
            extends BTreeKeySerializer<ReadToCloneMapping> implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Comparator<ReadToCloneMapping> comparator;

        public ReadToCloneMappingBtreeSerializer(Comparator<ReadToCloneMapping> comparator) {
            this.comparator = comparator;
        }

        @Override
        public void serialize(DataOutput out, int start, int end, Object[] keys) throws IOException {
            for (int i = start; i < end; ++i)
                write0(out, (ReadToCloneMapping) keys[i]);
        }

        @Override
        public Object[] deserialize(DataInput in, int start, int end, int size) throws IOException {
            Object[] r = new Object[size];
            for (int i = start; i < end; ++i)
                r[i] = read0(in);
            return r;
        }

        @Override
        public Comparator<ReadToCloneMapping> getComparator() {
            return comparator;
        }
    }

    public static class ReadToCloneMappingSerializer implements Serializer<ReadToCloneMapping> {
        @Override
        public void write(PrimitivO output, ReadToCloneMapping object) {
            write0(output, object);
        }

        @Override
        public ReadToCloneMapping read(PrimitivI input) {
            return read0(input);
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    private static void write0(DataOutput output, ReadToCloneMapping object) {
        try {
            output.writeLong(object.alignmentsId);
            output.writeLong(object.readId);
            output.writeInt(object.cloneIndex);
            output.writeByte(object.mappingType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ReadToCloneMapping read0(DataInput input) {
        try {
            long alignmentsIndex = input.readLong();
            long readId = input.readLong();
            int cloneIndex = input.readInt();
            byte mappingType = input.readByte();
            return new ReadToCloneMapping(alignmentsIndex, readId, cloneIndex, mappingType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
