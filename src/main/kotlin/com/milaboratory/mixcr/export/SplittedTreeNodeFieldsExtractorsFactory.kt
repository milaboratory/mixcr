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
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.SplittedNode

object SplittedTreeNodeFieldsExtractorsFactory : FieldExtractorsFactoryWithPresets<Wrapper>() {
    override val presets: Map<String, List<ExportFieldDescription>> = buildMap {
        this["min"] = listOf(
            ExportFieldDescription("-treeId"),
            ExportFieldDescription("-nodeId"),
            ExportFieldDescription("-parentId"),
            ExportFieldDescription("-distance", "germline"),
            ExportFieldDescription("-cloneId"),
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-readCount"),
            ExportFieldDescription("-vHit"),
            ExportFieldDescription("-dHit"),
            ExportFieldDescription("-jHit"),
            ExportFieldDescription("-cHit"),
            ExportFieldDescription("-nFeature", "CDR3")
        )

        this["full"] = listOf(
            ExportFieldDescription("-treeId"),
            ExportFieldDescription("-nodeId"),
            ExportFieldDescription("-isObserved"),
            ExportFieldDescription("-parentId"),
            ExportFieldDescription("-distance", "germline"),
            ExportFieldDescription("-cloneId"),
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-readCount"),
            ExportFieldDescription("-readFraction"),
            ExportFieldDescription("-targetSequences"),
            ExportFieldDescription("-targetQualities"),
            ExportFieldDescription("-vHitsWithScore"),
            ExportFieldDescription("-dHitsWithScore"),
            ExportFieldDescription("-jHitsWithScore"),
            ExportFieldDescription("-cHitsWithScore"),
            ExportFieldDescription("-vAlignments"),
            ExportFieldDescription("-dAlignments"),
            ExportFieldDescription("-jAlignments"),
            ExportFieldDescription("-cAlignments"),
            ExportFieldDescription("-allNFeatures"),
            ExportFieldDescription("-allAaFeatures"),
            ExportFieldDescription("-defaultAnchorPoints")
        )
    }

    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<FieldsCollection<Wrapper>> = buildList {
        this += SHMTreeFieldsExtractorsFactory.treeFields(false).map { it.fromProperty { tree } }
        this += SHMTreeNodeFieldsExtractor.nodeFields().map { it.fromProperty { node } }
        this += (VDJCObjectFieldExtractors.vdjcObjectFields(forTreesExport = true) +
                CloneFieldsExtractorsFactory.cloneFields(forTreesExport = true))
            .map { field ->
                field.fromProperty(descriptionMapper = { "$it (only for nodes with clones)" }) { node.clone?.clone }
            }
    }

    data class Wrapper(
        val tree: SHMTreeForPostanalysis<*>,
        val node: SplittedNode
    )
}
