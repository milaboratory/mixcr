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

import io.repseq.core.GeneFeature
import io.repseq.core.GeneType

/**
 * Parameters that can return gene features used in alignments for different gene types.
 *
 *
 * Parent of parameters of assemblers and VDJ aligners.
 */
interface HasFeatureToAlign {
    fun getFeatureToAlign(geneType: GeneType): GeneFeature?

    companion object {
        operator fun invoke(featuresToAlign: Map<GeneType, GeneFeature>) = object : HasFeatureToAlign {
            override fun getFeatureToAlign(geneType: GeneType): GeneFeature? = featuresToAlign[geneType]
        }
    }
}
