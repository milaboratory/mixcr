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
package com.milaboratory.mixcr.assembler.fullseq

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.core.sequence.quality.QualityTrimmerParameters
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.util.ParametersPresets

data class FullSeqAssemblerParameters @JsonCreator constructor(
    /**
     * Minimal quality fraction (variant may be marked significant if `variantQuality > totalSumQuality *
     * branchingMinimalQualityShare`
     */
    @param:JsonProperty("branchingMinimalQualityShare") val branchingMinimalQualityShare: Double,
    /**
     * Minimal variant quality threshold (variant may be marked significant if `variantQuality >
     * branchingMinimalSumQuality`
     */
    @param:JsonProperty("branchingMinimalSumQuality") val branchingMinimalSumQuality: Long,
    /**
     * Variant quality that guaranties that variant will be marked significant (even if other criteria are not
     * satisfied)
     */
    @param:JsonProperty("decisiveBranchingSumQualityThreshold") val decisiveBranchingSumQualityThreshold: Long,
    /**
     * Maximal number of not aligned nucleotides at the edge of sequence so that sequence is still considered aligned
     * "to the end"
     */
    @param:JsonProperty("alignedSequenceEdgeDelta") val alignedSequenceEdgeDelta: Int,
    /**
     * Number of nucleotides at the edges of alignments (with almost fully aligned seq2) that are "not trusted"
     */
    @param:JsonProperty("alignmentEdgeRegionSize") val alignmentEdgeRegionSize: Int,
    /**
     * Minimal fraction of non edge points in variant that must be reached to consider the variant significant
     */
    @param:JsonProperty("minimalNonEdgePointsFraction") val minimalNonEdgePointsFraction: Double,
    /**
     * Positions having mean normalized quality
     * (sum of quality scores for the variant / read count for the whole clonotype;
     * position coverage not taken into account) less then this value, will not be used for sub-cloning
     */
    @param:JsonProperty("minimalMeanNormalizedQuality") val minimalMeanNormalizedQuality: Double,
    /**
     * Positions having quality share less then this value, will not be represented in the output; used if no variants
     * are detected with standard pipeline
     */
    @param:JsonProperty("outputMinimalQualityShare") val outputMinimalQualityShare: Double,
    /**
     * Positions having sum quality less then this value, will not be represented in the output
     */
    @param:JsonProperty("outputMinimalSumQuality") val outputMinimalSumQuality: Long,
    /**
     * Gene feature limiting the set of positions where sufficient number of different nucleotides may split input
     * into several clonotypes. If position is not covered by the region, and significant disagreement between
     * nucleotides is observed, algorithm will produce "N" letter in the corresponding contig position to indicate the
     * ambiguity. Null - means no subcloning region, and guarantees one to one input to output clonotype correspondence.
     */
    @param:JsonProperty("subCloningRegions") val subCloningRegions: GeneFeatures?,
    /**
     * Limits the region of the sequence to assemble during the procedure, no nucleotides will be assembled outside it.
     * Null will result in assembly of the longest possible contig sequence.
     */
    @param:JsonProperty("assemblingRegions") val assemblingRegions: GeneFeatures?,
    /**
     * Used only if [.assemblingRegions] is not null. Sets filtering criteria to apply before outputting the
     * resulting clonotypes.
     */
    @param:JsonProperty("postFiltering") val postFiltering: PostFiltering,
    /**
     * Parameters of trimmer, that performs final processing of the output contigs
     */
    @param:JsonProperty("trimmingParameters") val trimmingParameters: QualityTrimmerParameters,
    /**
     * Minimal contiguous sequence length
     */
    @param:JsonProperty("minimalContigLength") val minimalContigLength: Int,
    /**
     * Assemble only parts of sequences covered by alignments
     */
    @param:JsonProperty("alignedRegionsOnly") val isAlignedRegionsOnly: Boolean
) {

    /**
     * Used only if [.assemblingRegions] is not null.
     */
    enum class PostFiltering {
        /**
         * Don't filter output clonotypes
         */
        NoFiltering,

        /**
         * Only clonotypes completely covering [.assemblingRegions] will be retained.
         */
        OnlyFullyAssembled,

        /**
         * Only clonotypes completely covering [.assemblingRegions] and having no "N" letters will be retained.
         */
        OnlyFullyDefined
    }

    companion object {
        @JvmStatic
        val presets = ParametersPresets<FullSeqAssemblerParameters>("full_seq_assembler_parameters")
    }
}
