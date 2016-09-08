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

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.CompatibilityIOReference;
import com.milaboratory.mixcr.reference.ReferenceCompatibilityIO;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.SerializersManager;

import java.util.EnumMap;
import java.util.Map;

public final class CompatibilityIO {
    private CompatibilityIO() {
    }

    public static void registerV3Serializers(SerializersManager manager) {
        registerV5Serializers(manager);
        ReferenceCompatibilityIO.registerV3BasicReferencePointSerializers(manager);
    }

    public static void registerV5Serializers(SerializersManager manager) {
        registerV6Serializers(manager);
        manager.registerCustomSerializer(VDJCAlignments.class, new VDJCAlignmentsSerializerV5());
    }

    public static void registerV6Serializers(SerializersManager manager) {
        registerV7Serializers(manager);
        CompatibilityIOReference.registerV6Serializers(manager);
    }

    public static void registerV7Serializers(SerializersManager manager) {
        manager.registerCustomSerializer(VDJCAlignments.class, new VDJCAlignmentsSerializerV7());
    }

    public static class VDJCAlignmentsSerializerV7 implements Serializer<VDJCAlignments> {
        @Override
        public void write(PrimitivO output, VDJCAlignments object) {
            output.writeObject(object.targets);
            output.writeObject(object.targetDescriptions);
            output.writeObject(object.originalSequences);
            output.writeByte(object.hits.size());
            for (Map.Entry<GeneType, VDJCHit[]> entry : object.hits.entrySet()) {
                output.writeObject(entry.getKey());
                output.writeObject(entry.getValue());
            }
            output.writeLong(object.readId);
        }

        @Override
        public VDJCAlignments read(PrimitivI input) {
            NSequenceWithQuality[] targets = input.readObject(NSequenceWithQuality[].class);
            String[] descriptions = input.readObject(String[].class);
            NSequenceWithQuality[] originalSequences = input.readObject(NSequenceWithQuality[].class);
            int size = input.readByte();
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
            for (int i = 0; i < size; i++) {
                GeneType key = input.readObject(GeneType.class);
                hits.put(key, input.readObject(VDJCHit[].class));
            }
            VDJCAlignments vdjcAlignments = new VDJCAlignments(input.readLong(), hits, targets);
            vdjcAlignments.setTargetDescriptions(descriptions);
            vdjcAlignments.setOriginalSequences(originalSequences);
            return vdjcAlignments;
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

    public static class VDJCAlignmentsSerializerV5 implements Serializer<VDJCAlignments> {
        @Override
        public void write(PrimitivO output, VDJCAlignments object) {
            output.writeObject(object.targets);
            output.writeObject(object.targetDescriptions);
            output.writeByte(object.hits.size());
            for (Map.Entry<GeneType, VDJCHit[]> entry : object.hits.entrySet()) {
                output.writeObject(entry.getKey());
                output.writeObject(entry.getValue());
            }
            output.writeLong(object.readId);
        }

        @Override
        public VDJCAlignments read(PrimitivI input) {
            NSequenceWithQuality[] targets = input.readObject(NSequenceWithQuality[].class);
            String[] descriptions = input.readObject(String[].class);
            int size = input.readByte();
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
            for (int i = 0; i < size; i++) {
                GeneType key = input.readObject(GeneType.class);
                hits.put(key, input.readObject(VDJCHit[].class));
            }
            VDJCAlignments vdjcAlignments = new VDJCAlignments(input.readLong(), hits, targets);
            vdjcAlignments.setTargetDescriptions(descriptions);
            return vdjcAlignments;
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
}
