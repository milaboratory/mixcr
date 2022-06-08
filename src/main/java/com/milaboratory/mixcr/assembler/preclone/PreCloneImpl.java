package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.ExtendedReferencePoints;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.*;

@Serializable(by = IO.PreCloneImplSerializer.class)
public final class PreCloneImpl extends PreCloneAbstract {
    /** Reference point for each of the clonal sequences */
    final ExtendedReferencePoints[] referencePoints;
    final int numberOfReads;

    public PreCloneImpl(long index, TagTuple coreKey, TagCount coreTagCount, TagCount fullTagCount,
                        NSequenceWithQuality[] clonalSequence,
                        Map<GeneType, List<GeneAndScore>> geneScores,
                        ExtendedReferencePoints[] referencePoints,
                        int numberOfReads) {
        super(index, coreKey, coreTagCount, fullTagCount, clonalSequence, geneScores);
        this.referencePoints = Objects.requireNonNull(referencePoints);
        this.numberOfReads = numberOfReads;
    }

    @Override
    public Range getRange(int csIdx, GeneFeature feature) {
        return referencePoints[csIdx].getRange(feature);
    }

    @Override
    public int getNumberOfReads() {
        return numberOfReads;
    }

    public PreCloneImpl withIndex(long index) {
        return new PreCloneImpl(index, coreKey, coreTagCount, fullTagCount, clonalSequence,
                geneScores, referencePoints, numberOfReads);
    }
}
