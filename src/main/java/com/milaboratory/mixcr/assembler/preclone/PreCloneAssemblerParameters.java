/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
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
            .minimalRecordCount(1)
            .build();
}
