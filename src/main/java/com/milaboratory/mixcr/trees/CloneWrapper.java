package com.milaboratory.mixcr.trees;

import com.google.common.collect.Sets;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.TargetPartitioning;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;

import static io.repseq.core.GeneType.*;

/**
 *
 */
@Serializable(by = CloneWrapper.SerializerImpl.class)
public class CloneWrapper {
    /**
     * Original clonotype
     */
    public final Clone clone;
    /**
     * Dataset serial number
     */
    public final int datasetId;
    final VJBase VJBase;

    public CloneWrapper(Clone clone, int datasetId, VJBase VJBase) {
        this.clone = clone;
        this.datasetId = datasetId;
        this.VJBase = VJBase;
    }

    public VDJCHit getHit(GeneType geneType) {
        String geneName = VJBase.getGeneName(geneType);
        return Arrays.stream(clone.getHits(geneType))
                .filter(it -> it.getGene().getName().equals(geneName))
                .findAny()
                .orElseThrow(IllegalArgumentException::new);
    }

    public NSequenceWithQuality getFeature(GeneFeature geneFeature) {
        VDJCPartitionedSequence[] partitionedTargets = new VDJCPartitionedSequence[clone.getTargets().length];
        EnumMap<GeneType, VDJCHit> topHits = new EnumMap<>(GeneType.class);
        for (GeneType geneType : Sets.newHashSet(Joining, Variable)) {
            topHits.put(geneType, getHit(geneType));
        }
        for (GeneType geneType : Sets.newHashSet(Constant, Diversity)) {
            VDJCHit[] hits = clone.getHits().get(geneType);
            if (hits != null && hits.length > 0)
                topHits.put(geneType, hits[0]);
        }
        for (int i = 0; i < clone.getTargets().length; ++i)
            partitionedTargets[i] = new VDJCPartitionedSequence(clone.getTarget(i), new TargetPartitioning(i, topHits));


        int tcf = getTargetContainingFeature(partitionedTargets, geneFeature);
        return tcf == -1 ? null : partitionedTargets[tcf].getFeature(geneFeature);
    }

    private int getTargetContainingFeature(VDJCPartitionedSequence[] partitionedTargets, GeneFeature feature) {
        NSequenceWithQuality tmp;
        int targetIndex = -1, quality = -1;
        for (int i = 0; i < clone.getTargets().length; ++i) {
            tmp = partitionedTargets[i].getFeature(feature);
            if (tmp != null && quality < tmp.getQuality().minValue())
                targetIndex = i;
        }
        return targetIndex;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloneWrapper that = (CloneWrapper) o;
        return datasetId == that.datasetId && Objects.equals(clone, that.clone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clone, datasetId);
    }

    int getRelativePosition(GeneType geneType, ReferencePoint referencePoint) {
        VDJCHit hit = getHit(geneType);
        return hit.getGene().getPartitioning()
                .getRelativePosition(hit.getAlignedFeature(), referencePoint);
    }

    public static class SerializerImpl implements Serializer<CloneWrapper> {
        @Override
        public void write(PrimitivO output, CloneWrapper object) {
            output.writeObject(object.clone);
            output.writeInt(object.datasetId);
            output.writeUTF(object.VJBase.VGeneName);
            output.writeUTF(object.VJBase.JGeneName);
            output.writeInt(object.VJBase.CDR3length);
        }

        @Override
        public CloneWrapper read(PrimitivI input) {
            return new CloneWrapper(
                    input.readObject(Clone.class),
                    input.readInt(),
                    new VJBase(input.readUTF(), input.readUTF(), input.readInt())
            );
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
