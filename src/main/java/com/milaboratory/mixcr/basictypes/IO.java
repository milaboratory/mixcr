/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.EnumMap;
import java.util.Map;

class IO {
    public static class VDJCHitSerializer implements Serializer<VDJCHit> {
        @Override
        public void write(PrimitivO output, VDJCHit object) {
            output.writeObject(object.getGene());
            output.writeObject(object.getAlignedFeature());
            output.writeVarInt(object.numberOfTargets());
            for (int i = object.numberOfTargets() - 1; i >= 0; --i)
                output.writeObject(object.getAlignment(i));
            output.writeFloat(object.getScore());
        }

        @Override
        public VDJCHit read(PrimitivI input) {
            VDJCGene gene = input.readObject(VDJCGene.class);
            GeneFeature alignedFeature = input.readObject(GeneFeature.class);
            int numberOfTargets = input.readVarInt();
            Alignment<NucleotideSequence>[] alignments = new Alignment[numberOfTargets];
            for (int i = numberOfTargets - 1; i >= 0; --i)
                alignments[i] = input.readObject(Alignment.class);
            float score = input.readFloat();
            return new VDJCHit(gene, alignments, alignedFeature, score);
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

    public static class VDJCAlignmentsSerializer implements Serializer<VDJCAlignments> {
        @Override
        public void write(PrimitivO output, VDJCAlignments object) {
            output.writeObject(object.targets);
            output.writeObject(object.originalReads);
            output.writeObject(object.history);
            output.writeByte(object.hits.size());
            for (Map.Entry<GeneType, VDJCHit[]> entry : object.hits.entrySet()) {
                output.writeObject(entry.getKey());
                output.writeObject(entry.getValue());
            }
            output.writeByte(object.mappingType);
            if (!ReadToCloneMapping.isDropped(object.mappingType))
                output.writeVarInt(object.cloneIndex);
        }

        @Override
        public VDJCAlignments read(PrimitivI input) {
            NSequenceWithQuality[] targets = input.readObject(NSequenceWithQuality[].class);
            SequenceRead[] originalReads = input.readObject(SequenceRead[].class);
            SequenceHistory[] history = input.readObject(SequenceHistory[].class);
            int size = input.readByte();
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
            for (int i = 0; i < size; i++) {
                GeneType key = input.readObject(GeneType.class);
                if (key == null)
                    throw new RuntimeException("Illegal file format.");
                hits.put(key, input.readObject(VDJCHit[].class));
            }
            byte mappingType = input.readByte();
            int cloneIndex = -1;
            if (!ReadToCloneMapping.isDropped(mappingType))
                cloneIndex = input.readVarInt();
            return new VDJCAlignments(hits, targets, history, originalReads, mappingType, cloneIndex);
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

    public static class CloneSerializer implements Serializer<Clone> {
        @Override
        public void write(PrimitivO output, Clone object) {
            output.writeObject(object.targets);
            output.writeByte(object.hits.size());
            for (Map.Entry<GeneType, VDJCHit[]> entry : object.hits.entrySet()) {
                output.writeObject(entry.getKey());
                output.writeObject(entry.getValue());
            }
            output.writeDouble(object.count);
            output.writeInt(object.id);
        }

        @Override
        public Clone read(PrimitivI input) {
            NSequenceWithQuality[] targets = input.readObject(NSequenceWithQuality[].class);
            int size = input.readByte();
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
            for (int i = 0; i < size; i++) {
                GeneType key = input.readObject(GeneType.class);
                hits.put(key, input.readObject(VDJCHit[].class));
            }
            double count = input.readDouble();
            int id = input.readInt();
            return new Clone(targets, hits, count, id);
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

    public static void writeGT2GFMap(PrimitivO output, EnumMap<GeneType, GeneFeature> gf2gt) {
        output.writeInt(gf2gt.size());
        for (Map.Entry<GeneType, GeneFeature> entry : gf2gt.entrySet()) {
            output.writeObject(entry.getKey());
            output.writeObject(entry.getValue());
        }
    }

    public static EnumMap<GeneType, GeneFeature> readGF2GTMap(PrimitivI input) {
        int count = input.readInt();
        EnumMap<GeneType, GeneFeature> map = new EnumMap<GeneType, GeneFeature>(GeneType.class);
        for (int i = 0; i < count; i++) {
            GeneType gt = input.readObject(GeneType.class);
            GeneFeature gf = input.readObject(GeneFeature.class);
            map.put(gt, gf);
        }
        return map;
    }
}
