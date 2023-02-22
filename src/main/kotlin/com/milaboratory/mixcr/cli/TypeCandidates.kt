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
package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.Presets
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.ReferencePoint

abstract class TypeCandidates(
    private val candidates: Iterable<String>
) : Iterable<String> by candidates


object GeneFeaturesCandidates : TypeCandidates(
    GeneFeature.getFeaturesByName().keys
)

object ReferencePointsCandidates : TypeCandidates(
    ReferencePoint.DefaultReferencePoints.map { ReferencePoint.getNameByPoint(it) }
)

object ReferencePointsCandidatesAndGeneType : TypeCandidates(
    ReferencePoint.DefaultReferencePoints.map { ReferencePoint.getNameByPoint(it) } + listOf("C", "J")
)

object ChainsCandidates : TypeCandidates(
    Chains.WELL_KNOWN_CHAINS_MAP.keys
)

object PresetsCandidates : TypeCandidates(
    Presets.visiblePresets
)
