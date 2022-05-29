package com.milaboratory.mixcr.assembler.preclone;

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
}
