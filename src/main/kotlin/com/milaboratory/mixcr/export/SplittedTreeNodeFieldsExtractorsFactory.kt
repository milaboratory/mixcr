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
            FieldCommandArgs("-distance", "germline"),
            FieldCommandArgs("-cloneId"),
            FieldCommandArgs("-fileName"),
            FieldCommandArgs("-readCount"),
            FieldCommandArgs("-vHit"),
            FieldCommandArgs("-dHit"),
            FieldCommandArgs("-jHit"),
            FieldCommandArgs("-cHit"),
            FieldCommandArgs("-nFeature", "CDR3", "node")
        )

        this["full"] = listOf(
            FieldCommandArgs("-treeId"),
            FieldCommandArgs("-nodeId"),
            FieldCommandArgs("-isObserved"),
            FieldCommandArgs("-parentId"),
            FieldCommandArgs("-distance", "germline"),
            FieldCommandArgs("-cloneId"),
            FieldCommandArgs("-fileName"),
            FieldCommandArgs("-readCount"),
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
            FieldCommandArgs("-nFeature", "FR1", "node"),
            FieldCommandArgs("-aaFeature", "CDR1", "node"),
            FieldCommandArgs("-nFeature", "CDR1", "node"),
            FieldCommandArgs("-aaFeature", "FR1", "node"),
            FieldCommandArgs("-nFeature", "FR2", "node"),
            FieldCommandArgs("-aaFeature", "FR2", "node"),
            FieldCommandArgs("-nFeature", "CDR2", "node"),
            FieldCommandArgs("-aaFeature", "CDR2", "node"),
            FieldCommandArgs("-nFeature", "FR3", "node"),
            FieldCommandArgs("-aaFeature", "FR3", "node"),
            FieldCommandArgs("-nFeature", "CDR3", "node"),
            FieldCommandArgs("-aaFeature", "CDR3", "node"),
            FieldCommandArgs("-nFeature", "FR4", "node"),
            FieldCommandArgs("-aaFeature", "FR4", "node"),
            FieldCommandArgs("-defaultAnchorPoints")
        )
    }

    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<Field<Wrapper>> = buildList {
        this += SHMTreeFieldsExtractorsFactory.treeFields(false).map { it.fromProperty { tree } }
        this += SHMTreeNodeFieldsExtractor.nodeFields().map { it.fromProperty { node } }
        this += (VDJCObjectFieldExtractors.vdjcObjectFields(forTreesExport = true) +
                CloneFieldsExtractorsFactory.cloneFields(forTreesExport = true))
            .map { field ->
                field.fromProperty(descriptionMapper = { "$it (only for nodes with clones)" }) { node.clone?.clone }
            }
    }

    data class Wrapper(
        val tree: SHMTreeForPostanalysis,
        val node: SHMTreeForPostanalysis.SplittedNode
    )
}
