package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneType;

import java.util.List;
import java.util.Map;

@Serializable(by = IO.PreCloneSerializer.class)
public final class PreClone {
    /** Pre-clonotype id */
    public long id;
    /** Core key of the alignments group */
    public final TagTuple coreKey;
    /** Tag counter aggregating information about alignments with clonal sequence */
    public final TagCount coreTagCount;
    /** Tag counter aggregating information across all alignments assigned to this pre-clone */
    public final TagCount fullTagCount;
    /** Assembled clonal sequence */
    public final NSequenceWithQuality[] clonalSequence;
    /** Aggregated V, J, C gene scoring and content information */
    public final Map<GeneType, List<GeneAndScore>> geneScores;

    public PreClone(long id, TagTuple coreKey, TagCount coreTagCount, TagCount fullTagCount,
                    NSequenceWithQuality[] clonalSequence,
                    Map<GeneType, List<GeneAndScore>> geneScores) {
        this.id = id;
        this.coreKey = coreKey;
        this.coreTagCount = coreTagCount;
        this.fullTagCount = fullTagCount;
        this.clonalSequence = clonalSequence;
        this.geneScores = geneScores;
    }

    public long getId() {
        return id;
    }
}
