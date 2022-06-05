package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import io.repseq.core.GeneType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class IO {
    private IO() {
    }

    public static final class PreCloneSerializer implements Serializer<PreClone> {
        @Override
        public void write(PrimitivO output, PreClone obj) {
            output.writeLong(obj.index);
            output.writeObject(obj.coreKey);
            output.writeObject(obj.clonalSequence);
            output.writeObject(obj.coreTagCount);
            output.writeObject(obj.fullTagCount);
            output.writeInt(obj.geneScores.size());
            for (Map.Entry<GeneType, List<GeneAndScore>> e : obj.geneScores.entrySet()) {
                output.writeObject(e.getKey());
                output.writeInt(e.getValue().size());
                for (GeneAndScore gs : e.getValue())
                    output.writeObject(gs);
            }
        }

        @Override
        public PreClone read(PrimitivI input) {
            long index = input.readLong();
            TagTuple coreKey = input.readObject(TagTuple.class);
            NSequenceWithQuality[] clonalSequence = input.readObject(NSequenceWithQuality[].class);
            TagCount coreTagCount = input.readObject(TagCount.class);
            TagCount fullTagCount = input.readObject(TagCount.class);
            int count0 = input.readInt();
            EnumMap<GeneType, List<GeneAndScore>> gsss = new EnumMap<>(GeneType.class);
            for (int i0 = 0; i0 < count0; i0++) {
                GeneType gt = input.readObject(GeneType.class);
                int count1 = input.readInt();
                List<GeneAndScore> gss = new ArrayList<>();
                for (int i1 = 0; i1 < count1; i1++)
                    gss.add(input.readObject(GeneAndScore.class));
                gsss.put(gt, gss);
            }
            return new PreClone(index, coreKey, coreTagCount, fullTagCount, clonalSequence, gsss);
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
