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
package com.milaboratory.mixcr.trees

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCGene

fun List<CloneReader>.constructStateBuilder(): PrimitivIOStateBuilder {
    check(map { it.alignerParameters }.distinct().size == 1) {
        "Files was prepared with different alignerParameters"
    }
    val stateBuilder = PrimitivIOStateBuilder()
    IOUtil.registerGeneReferences(
        stateBuilder,
        asSequence().flatMap { it.usedGenes }.toSet(),
        first().alignerParameters
    )
    return stateBuilder
}

fun HasFeatureToAlign.constructStateBuilder(usedGenes: Collection<VDJCGene>): PrimitivIOStateBuilder {
    val stateBuilder = PrimitivIOStateBuilder()
    IOUtil.registerGeneReferences(
        stateBuilder,
        usedGenes,
        this
    )
    return stateBuilder
}

fun Clone.formsAllRefPointsInCDR3(VJBase: VJBase): Boolean =
    getFeature(GeneFeature.CDR3, VJBase) != null && getFeature(GeneFeature.VJJunction, VJBase) != null

