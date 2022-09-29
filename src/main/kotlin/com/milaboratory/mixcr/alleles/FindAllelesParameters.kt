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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.milaboratory.mixcr.util.ParametersPresets
import com.milaboratory.mixcr.util.VJPair

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
data class FindAllelesParameters(
    /**
     * Use only clones with count more than parameter
     */
    val useClonesWithCountGreaterThen: Int,
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    val productiveOnly: Boolean,
    val searchAlleleParameter: BCellsAlleleSearchParameters,
    val searchMutationsInCDR3: SearchMutationsInCDR3Params
) {
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
         * Letter must be represented by not less than `minDiversity` diversity of complimentary gene
         */
        val minDiversity: Int,
    )

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class BCellsAlleleSearchParameters(
        /**
         * Alleles filtered that min and max diversity of result are bound by this ratio
         */
        val minDiversityRatioBetweenAlleles: Double,
        /**
         * On decision about clone matching to allele will check relation between score penalties between the best and the next alleles.
         */
        val minRelativePenaltyBetweenAllelesForCloneAlign: Double,
        /**
         * After an allele is found, it will be enriched with mutations that exists in this portion of clones that aligned on the allele.
         */
        val diversityRatioToSearchCommonMutationsInAnAllele: Double,
        /**
         * Min count
         */
        val minCountOfNaiveClonesToAddAllele: Int,
        val diversityThresholds: VJPair<DiversityThresholds>,
        val regressionFilter: RegressionFilter
    ) {

        data class DiversityThresholds(
            /**
             * Min diversity of mutation for it may be considered as a candidate for allele mutation
             */
            val minDiversityForMutation: Int,
            /**
             * Filter allele candidates with diversity less than this parameter
             */
            val minDiversityForAllele: Int,
            /**
             * If diversity of zero allele greater or equal to this, than it will not be tested by diversity ratio
             */
            val diversityForSkipTestForRatioForZeroAllele: Int,
        )

        @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "type"
        )
        sealed interface RegressionFilter {
            @JsonTypeName("noop")
            class NoOP : RegressionFilter {
                override fun equals(other: Any?): Boolean = other is NoOP
                override fun hashCode(): Int = 0
            }

            sealed class Filter : RegressionFilter {
                /**
                 * Window size that will be used for build regression of mutations frequency vs count of mutations
                 */
                abstract val windowSizeForRegression: Int

                /**
                 * Maximum absent points in a window to build regression
                 */
                abstract val allowedSkippedPointsInRegression: Int

                /**
                 * Mutations will be considered as a candidate for allele mutation if p-value of t-test of mutation frequency regression will be more than this parameter
                 */
                abstract val minPValue: Double

                /**
                 * Mutations will be considered as a candidate for allele mutation if y-intersect of mutation frequency regression will be more than this parameter and slope exists
                 */
                abstract val minYInterceptForHeterozygous: Double

                /**
                 * Mutations will be considered as a candidate for allele mutation if y-intersect of mutation frequency regression will be more than this parameter and no slope
                 */
                abstract val minYInterceptForHomozygous: Double

                /**
                 * Max slope for test that mutation is a part of homozygous allele
                 */
                abstract val maxSlopeForHomozygous: Double
            }

            @JsonTypeName("byCount")
            class ByCount(
                override val windowSizeForRegression: Int,
                override val allowedSkippedPointsInRegression: Int,
                override val minPValue: Double,
                override val minYInterceptForHeterozygous: Double,
                override val minYInterceptForHomozygous: Double,
                override val maxSlopeForHomozygous: Double,
            ) : Filter()

            @JsonTypeName("byDiversity")
            class ByDiversity(
                override val windowSizeForRegression: Int,
                override val allowedSkippedPointsInRegression: Int,
                override val minPValue: Double,
                override val minYInterceptForHeterozygous: Double,
                override val minYInterceptForHomozygous: Double,
                override val maxSlopeForHomozygous: Double,
            ) : Filter()
        }
    }

    companion object {
        val presets = ParametersPresets<FindAllelesParameters>("find_alleles_parameters")
    }
}
