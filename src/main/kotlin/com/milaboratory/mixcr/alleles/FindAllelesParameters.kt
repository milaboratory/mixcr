/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.milaboratory.mixcr.util.ParametersPresets

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
data class FindAllelesParameters(
    val filterForDataWithUmi: Filter,
    val filterForDataWithoutUmi: Filter,
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    val productiveOnly: Boolean,
    val searchAlleleParameter: AlleleMutationsSearchParameters,
    val searchMutationsInCDR3: SearchMutationsInCDR3Params?
) {
    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class Filter(
        /**
         * Use only clones with count greater or equal to then this parameter
         */
        val useClonesWithCountGreaterThen: Int,
    )

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class SearchMutationsInCDR3Params(
        /**
         * Letter must be represented in not less than `minClonesCount` clones
         */
        val minClonesCount: Int,
        /**
         * Portion of clones from group that must have the same letter
         */
        val minPartOfTheSameLetter: Double,
        /**
         * Letter must be represented by not less than `minDiversity` percentage of diversity by complimentary gene
         */
        val minDiversity: Double,
    )

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class AlleleMutationsSearchParameters(
        /**
         * Percentage to get top of alleles by diversity
         */
        val topByDiversity: Double,
        /**
         * On decision about clone matching to allele will check relation between score penalties between the best and the next alleles.
         */
        val minRelativePenaltyBetweenAllelesForCloneAlign: Double,
        /**
         * After an allele is found, it will be enriched with mutations that exists in this portion of clones that aligned on the allele.
         */
        val diversityRatioToSearchCommonMutationsInAnAllele: Double,
        /**
         * Alleles will be filtered by min count of clones that are naive by complementary gene
         */
        val minCountOfNaiveClonesToAddAllele: Int,
        val diversityThresholds: DiversityThresholds
    ) {

        data class DiversityThresholds(
            /**
             * Min percentage from max diversity of mutation for it may be considered as a candidate for allele mutation
             */
            val minDiversityForMutation: Double,
            /**
             * Filter out allele candidates with percentage from max diversity less than this parameter
             */
            val minDiversityForAllele: Double,
            /**
             * If percentage from max diversity of zero allele greater or equal to this, than it will not be tested by diversity ratio
             */
            val diversityForSkipTestForRatioForZeroAllele: Double,
        )
    }

    companion object {
        val presets = ParametersPresets<FindAllelesParameters>("find_alleles_parameters")
    }
}
