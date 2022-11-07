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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.mitool.consensus.AAssemblerParameters;
import com.milaboratory.mitool.consensus.GConsensusAssemblerParameters;

import java.util.Objects;
import java.util.function.Function;

public final class PreCloneAssemblerParameters {
    /** Parameters to pre-assemble clone assembling feature sequence inside read groups having the same tags */
    @JsonProperty("assembler")
    @JsonMerge
    final GConsensusAssemblerParameters assembler;

    /**
     * Only pre-clones having at least this share among reads with the same tag suffix will be preserved.
     * This option is useful when assembling consensuses inside cell groups, but still want to decontaminate
     * results using molecular barcodes.
     */
    @JsonProperty("minTagSuffixShare")
    final float minTagSuffixShare;

    @JsonCreator
    public PreCloneAssemblerParameters(
            @JsonProperty("assembler") @JsonMerge GConsensusAssemblerParameters assembler,
            @JsonProperty("minTagSuffixShare") float minTagSuffixShare) {
        this.assembler = Objects.requireNonNull(assembler);
        this.minTagSuffixShare = minTagSuffixShare;
    }

    public GConsensusAssemblerParameters getAssembler() {
        return assembler;
    }

    public float getMinTagSuffixShare() {
        return minTagSuffixShare;
    }

    public PreCloneAssemblerParameters withAssembler(GConsensusAssemblerParameters assembler) {
        return new PreCloneAssemblerParameters(assembler, minTagSuffixShare);
    }

    public PreCloneAssemblerParameters mapAssembler(Function<GConsensusAssemblerParameters, GConsensusAssemblerParameters> assemblerMapper) {
        return withAssembler(assemblerMapper.apply(assembler));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreCloneAssemblerParameters that = (PreCloneAssemblerParameters) o;
        return Float.compare(that.minTagSuffixShare, minTagSuffixShare) == 0 && assembler.equals(that.assembler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assembler, minTagSuffixShare);
    }

    private static final AAssemblerParameters DefaultAAssemblerParams = AAssemblerParameters.builder()
            .bandWidth(4)
            .scoring(LinearGapAlignmentScoring.getNucleotideBLASTScoring(-14))
            .minAlignmentScore(40)
            .maxNormalizedAlignmentPenalty(0.15f)
            .trimReferenceRegion(false)
            .maxQuality((byte) 45)
            .build();

    private static final GConsensusAssemblerParameters DefaultGConsensusAssemblerParameters = GConsensusAssemblerParameters.builder()
            .aAssemblerParameters(DefaultAAssemblerParams)
            .maxIterations(6)

            // Consensus diversity control
            .minAltSeedQualityScore((byte) 11)
            .minAltSeedNormalizedPenalty(0.35f)
            .altSeedPenaltyTolerance(0.3f)

            // Consensus filtering
            .minRecordSharePerConsensus(0.01f)
            .minRecordsPerConsensus(1)
            .minRecursiveRecordShare(0.35f)

            .build();

    /**
     * Returns default global assembler parameters for by-cell (byCell = true) or by molecule (byCell = false) assembly
     * scenario
     */
    public static PreCloneAssemblerParameters getDefaultParameters(boolean byCell) {
        if (byCell)
            return new PreCloneAssemblerParameters(
                    DefaultGConsensusAssemblerParameters.toBuilder()
                            .minRecordSharePerConsensus(.01f)
                            .minRecursiveRecordShare(.2f)
                            .maxConsensuses(0) // don't limit
                            .build(),
                    0.8f); // apply secondary UMI-based filtering
        else
            return new PreCloneAssemblerParameters(
                    DefaultGConsensusAssemblerParameters.toBuilder()
                            .minRecordSharePerConsensus(.2f)
                            .minRecursiveRecordShare(.4f)
                            .maxConsensuses(3)
                            .build(),
                    0.0f);
    }
}
