package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import io.repseq.core.GeneType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class PreCloneAbstract implements PreClone {
    final long index;
    final TagTuple coreKey;
    final TagCount coreTagCount;
    final TagCount fullTagCount;
    final NSequenceWithQuality[] clonalSequence;
    final Map<GeneType, List<GeneAndScore>> geneScores;

    public PreCloneAbstract(long index, TagTuple coreKey, TagCount coreTagCount, TagCount fullTagCount, NSequenceWithQuality[] clonalSequence, Map<GeneType, List<GeneAndScore>> geneScores) {
        this.index = index;
        this.coreKey = Objects.requireNonNull(coreKey);
        this.coreTagCount = Objects.requireNonNull(coreTagCount);
        this.fullTagCount = Objects.requireNonNull(fullTagCount);
        this.clonalSequence = Objects.requireNonNull(clonalSequence);
        this.geneScores = Objects.requireNonNull(geneScores);
    }

    @Override
    public long getIndex() {
        return index;
    }

    @Override
    public TagTuple getCoreKey() {
        return coreKey;
    }

    @Override
    public TagCount getCoreTagCount() {
        return coreTagCount;
    }

    @Override
    public TagCount getFullTagCount() {
        return fullTagCount;
    }

    @Override
    public NSequenceWithQuality[] getClonalSequence() {
        return clonalSequence;
    }

    @Override
    public Map<GeneType, List<GeneAndScore>> getGeneScores() {
        return geneScores;
    }
}
