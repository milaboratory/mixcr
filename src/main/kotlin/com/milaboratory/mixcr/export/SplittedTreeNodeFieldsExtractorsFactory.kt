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
package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.export.SplittedTreeNodeFieldsExtractorsFactory.Wrapper
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis

object SplittedTreeNodeFieldsExtractorsFactory : FieldExtractorsFactory<Wrapper>() {
    override val presets: Map<String, List<FieldCommandArgs>> = buildMap {
        this["min"] = listOf(
            FieldCommandArgs("-treeId"),
            FieldCommandArgs("-nodeId"),
            FieldCommandArgs("-parentId"),
            FieldCommandArgs("-distance germline"),
            FieldCommandArgs("-cloneId"),
            FieldCommandArgs("-fileName"),
            FieldCommandArgs("-count"),
            FieldCommandArgs("-vHit"),
            FieldCommandArgs("-dHit"),
            FieldCommandArgs("-jHit"),
            FieldCommandArgs("-cHit"),
            FieldCommandArgs("-nFeature", "CDR3")
        )

        this["full"] = listOf(
            FieldCommandArgs("-treeId"),
            FieldCommandArgs("-nodeId"),
            FieldCommandArgs("-parentId"),
            FieldCommandArgs("-distance germline"),
            FieldCommandArgs("-cloneId"),
            FieldCommandArgs("-fileName"),
            FieldCommandArgs("-count"),
            FieldCommandArgs("-targetSequences"),
            FieldCommandArgs("-targetQualities"),
            FieldCommandArgs("-vHitsWithScore"),
            FieldCommandArgs("-dHitsWithScore"),
            FieldCommandArgs("-jHitsWithScore"),
            FieldCommandArgs("-cHitsWithScore"),
            FieldCommandArgs("-vAlignments"),
            FieldCommandArgs("-dAlignments"),
            FieldCommandArgs("-jAlignments"),
            FieldCommandArgs("-cAlignments"),
            FieldCommandArgs("-nFeature", "FR1"),
            FieldCommandArgs("-minFeatureQuality", "FR1"),
            FieldCommandArgs("-nFeature", "CDR1"),
            FieldCommandArgs("-minFeatureQuality", "CDR1"),
            FieldCommandArgs("-nFeature", "FR2"),
            FieldCommandArgs("-minFeatureQuality", "FR2"),
            FieldCommandArgs("-nFeature", "CDR2"),
            FieldCommandArgs("-minFeatureQuality", "CDR2"),
            FieldCommandArgs("-nFeature", "FR3"),
            FieldCommandArgs("-minFeatureQuality", "FR3"),
            FieldCommandArgs("-nFeature", "CDR3"),
            FieldCommandArgs("-minFeatureQuality", "CDR3"),
            FieldCommandArgs("-nFeature", "FR4"),
            FieldCommandArgs("-minFeatureQuality", "FR4"),
            FieldCommandArgs("-aaFeature", "FR1"),
            FieldCommandArgs("-aaFeature", "CDR1"),
            FieldCommandArgs("-aaFeature", "FR2"),
            FieldCommandArgs("-aaFeature", "CDR2"),
            FieldCommandArgs("-aaFeature", "FR3"),
            FieldCommandArgs("-aaFeature", "CDR3"),
            FieldCommandArgs("-aaFeature", "FR4"),
            FieldCommandArgs("-defaultAnchorPoints")
        )
    }

    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<Field<Wrapper>> = buildList {
        this += SHMTreeFieldsExtractorsFactory.fields.map { it.fromProperty { tree } }
        this += SHMTreeNodeFieldsExtractor.nodeFields().map { it.fromProperty { node } }
        this += (VDJCObjectFieldExtractors.vdjcObjectFields(forTreesExport = true) + CloneFieldsExtractorsFactory.cloneFields())
            .map { field ->
                field.fromProperty(headerMapper = { "$it (only for nodes with clones)" }) { node.clone?.clone }
            }
    }

    data class Wrapper(
        val tree: SHMTreeForPostanalysis,
        val node: SHMTreeForPostanalysis.SplittedNode
    )
}
