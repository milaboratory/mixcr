package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.mitool.consensus.AAssemblerParameters;
import com.milaboratory.mitool.consensus.GConsensusAssemblerParameters;
import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import io.repseq.core.GeneFeature;

public final class PreCloneAssemblerParameters {
    final GConsensusAssemblerParameters assemblerParameters;
    final HasRelativeMinScore relativeMinScores;
    final GeneFeature[] assemblingFeatures;
    final int groupingLevel;

    public PreCloneAssemblerParameters(GConsensusAssemblerParameters assemblerParameters,
                                       HasRelativeMinScore relativeMinScores,
                                       GeneFeature[] assemblingFeatures, int groupingLevel) {
        this.assemblerParameters = assemblerParameters;
        this.relativeMinScores = relativeMinScores;
        this.assemblingFeatures = assemblingFeatures;
        this.groupingLevel = groupingLevel;
    }

    public static final AAssemblerParameters DefaultAAssemblerParams = AAssemblerParameters.builder()
            .bandWidth(4)
            .scoring(LinearGapAlignmentScoring.getNucleotideBLASTScoring(-14))
            .minAlignmentScore(40)
            .maxAlignmentPenalty(33)
            .trimMinimalSumQuality(20)
            .trimReferenceRegion(false)
            .maxQuality((byte) 45)
            .build();

    public static final GConsensusAssemblerParameters DefaultGConsensusAssemblerParameters = GConsensusAssemblerParameters.builder()
            .aAssemblerParameters(DefaultAAssemblerParams)
            .maxIterations(4)
            .minAltSeedQualityScore((byte) 11)
            .minimalRecordShare(0.1)
            .minimalRecordCount(2)
            .build();
}
