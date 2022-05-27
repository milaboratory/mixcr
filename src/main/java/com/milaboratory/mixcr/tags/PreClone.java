package com.milaboratory.mixcr.tags;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.assembler.GeneAndScore;
import com.milaboratory.mixcr.basictypes.tag.TagCounter;
import com.milaboratory.util.BitArray;
import io.repseq.core.GeneType;

import java.util.List;
import java.util.Map;

public final class PreClone {
    /** Tag counter aggregating information about alignments with clonal sequence */
    public final TagCounter coreTagCounter;
    /** Tag counter aggregating information across all alignments assigned to this pre-clone */
    public final TagCounter fullTagCounter;
    /** Assembled clonal sequence */
    public final NSequenceWithQuality[] clonalSequence;
    /** Aggregated V, J, C gene scoring and content information */
    public final Map<GeneType, List<GeneAndScore>> geneScores;
    /** Ids of alignments assigned to this pre-clone */
    public final BitArray alignments;

    public PreClone(TagCounter coreTagCounter, TagCounter fullTagCounter, NSequenceWithQuality[] clonalSequence,
                    Map<GeneType, List<GeneAndScore>> geneScores, BitArray alignments) {
        this.coreTagCounter = coreTagCounter;
        this.fullTagCounter = fullTagCounter;
        this.clonalSequence = clonalSequence;
        this.geneScores = geneScores;
        this.alignments = alignments;
    }
}
