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
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.primitivio.annotations.Serializable

/**
 *
 */
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE
)
@Serializable(asJson = true)
data class FindAllelesParameters @JsonCreator constructor(
    /**
     * Min diversity for clones with the same mutations to be considered as an allele.
     */
    @param:JsonProperty("minDiversityForNativeAlleles") val minDiversityForNativeAlleles: Int,
    @param:JsonProperty("filterClonesWithCountLessThan") val filterClonesWithCountLessThan: Int,
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    @param:JsonProperty("productiveOnly") val productiveOnly: Boolean,
    /**
     * Min portion of clones to determinate common alignment ranges.
     */
    @param:JsonProperty("minPortionOfClonesForCommonAlignmentRanges") val minPortionOfClonesForCommonAlignmentRanges: Double,


    ) : java.io.Serializable
