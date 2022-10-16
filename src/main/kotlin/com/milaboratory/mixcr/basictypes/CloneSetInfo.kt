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
package com.milaboratory.mixcr.basictypes

import com.milaboratory.mixcr.basictypes.tag.TagCount
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType

interface CloneSetInfo : MiXCRFileInfo, HasFeatureToAlign {
    val totalCount: Double

    val totalTagCounts: TagCount?

    fun getTagDiversity(level: Int): Int

    override fun getFeatureToAlign(geneType: GeneType): GeneFeature =
        header.alignerParameters.getFeatureToAlign(geneType)

    val assemblingFeatures: Array<GeneFeature>
        get() = header.assemblerParameters!!.assemblingFeatures
}
