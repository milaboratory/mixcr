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
package com.milaboratory.mixcr.alleles

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.mixcr.util.ParametersPresets
import com.milaboratory.primitivio.annotations.Serializable

/**
 *
 */
@Serializable(asJson = true)
data class FindAllelesParameters @JsonCreator constructor(
    /**
     * Use only clones with count more than parameter
     */
    @param:JsonProperty("useClonesWithCountMoreThen") val useClonesWithCountMoreThen: Int,
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    @param:JsonProperty("productiveOnly") val productiveOnly: Boolean,
    /**
     * Window size that will be used for build regression of mutations frequency vs count of mutations
     */
    @param:JsonProperty("windowSizeForRegression") val windowSizeForRegression: Int,
    /**
     * Maximum absent points in a window to build regression
     */
    @param:JsonProperty("allowedSkippedPointsInRegression") val allowedSkippedPointsInRegression: Int,
    /**
     * Alleles filtered that min and max diversity of result are bound by this ratio
     */
    @param:JsonProperty("minDiversityRatioBetweenAlleles") val minDiversityRatioBetweenAlleles: Double,
    /**
     * Min diversity of mutation for it may be considered as a candidate for allele mutation
     */
    @param:JsonProperty("minDiversityForMutation") val minDiversityForMutation: Int,
    /**
     * Filter allele candidates with diversity less than this parameter
     */
    @param:JsonProperty("minDiversityForAllele") val minDiversityForAllele: Int,
    /**
     * Mutations will be considered as a candidate for allele mutation if p-value of t-test of mutation frequency regression will be more than this parameter
     */
    @param:JsonProperty("minPValueForRegression") val minPValueForRegression: Double,
    /**
     * Mutations will be considered as a candidate for allele mutation if y-intersect of mutation frequency regression will be more than this parameter
     */
    @param:JsonProperty("minYIntersect") val minYIntersect: Double,
    /**
     * After an allele is found, it will be enriched with mutations that exists in this portion of clones that aligned on the allele.
     */
    @param:JsonProperty("portionOfClonesToSearchCommonMutationsInAnAllele") val portionOfClonesToSearchCommonMutationsInAnAllele: Double,
    @param:JsonProperty("searchMutationsInCDR3") val searchMutationsInCDR3: SearchMutationsInCDR3Params
) {
    class SearchMutationsInCDR3Params @JsonCreator constructor(
        /**
         * Letter must be represented in not less than `minClonesCount` clones
         */
        @param:JsonProperty("minClonesCount") val minClonesCount: Int,
        /**
         * Portion of clones from group that must have the same letter
         */
        @param:JsonProperty("minPartOfTheSameLetter") val minPartOfTheSameLetter: Double,
        /**
         * Letter must be represented by not less than `minDiversity` diversity of complimentary gene
         */
        @param:JsonProperty("minDiversity") val minDiversity: Int,
    )

    companion object {
        val presets = ParametersPresets<FindAllelesParameters>("find_alleles_parameters")
    }
}
