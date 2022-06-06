package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.List;
import java.util.Map;

public interface PreClone {
    long getIndex();

    TagTuple getCoreKey();

    TagCount getCoreTagCount();

    TagCount getFullTagCount();

    NSequenceWithQuality[] getClonalSequence();

    Map<GeneType, List<GeneAndScore>> getGeneScores();

    List<GeneAndScore> getGeneScores(GeneType geneType);

    GeneAndScore getBestHit(GeneType geneType);

    VDJCGeneId getBestGene(GeneType geneType);

    Range getRange(int csIdx, GeneFeature feature);
}
