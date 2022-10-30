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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.export

import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.export.GeneFeaturesRangeUtil.geneFeaturesBetween
import com.milaboratory.mixcr.export.ParametersFactory.nodeTypeParam
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.Base
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.VJ_REFERENCE
import kotlin.collections.set
import kotlin.math.log2

object SHMTreeFieldsExtractorsFactory : FieldExtractorsFactoryWithPresets<SHMTreeForPostanalysis<*>>() {
    override val presets: Map<String, List<ExportFieldDescription>> = buildMap {
        this["full"] = listOf(
            ExportFieldDescription("-treeId"),
            ExportFieldDescription("-uniqClonesCount"),
            ExportFieldDescription("-totalClonesCount"),
            ExportFieldDescription("-vHit"),
            ExportFieldDescription("-jHit"),
            ExportFieldDescription("-nFeature", "CDR3", "mrca"),
            ExportFieldDescription("-aaFeature", "CDR3", "mrca")
        )

        this["min"] = listOf(
            ExportFieldDescription("-treeId"),
            ExportFieldDescription("-vHit"),
            ExportFieldDescription("-jHit"),
        )
    }

    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<FieldsCollection<SHMTreeForPostanalysis<*>>> = treeFields(true)

    fun treeFields(withTargetFeatures: Boolean): List<FieldsCollection<SHMTreeForPostanalysis<*>>> = buildList {
        this += Field(
            Order.treeMainParams + 100,
            "-treeId",
            "SHM tree id",
            "treeId"
        ) { it.meta.treeId.toString() }

        this += Field(
            Order.treeMainParams + 200,
            "-uniqClonesCount",
            "Number of uniq clones in the SHM tree",
            "uniqClonesCount"
        ) { shmTree ->
            shmTree.tree.allNodes().sumOf { it.node.content.clones.count() }.toString()
        }

        this += Field(
            Order.treeMainParams + 300,
            "-totalClonesCount",
            "Total sum of counts of clones in the SHM tree",
            "totalClonesCount"
        ) { shmTree ->
            shmTree.tree.allNodes().sumOf { (_, node) -> node.content.clones.sumOf { it.clone.count } }.toString()
        }

        VJ_REFERENCE.forEach { type ->
            val l = type.letter
            this += Field(
                Order.orderForBestHit(type),
                "-${l.lowercaseChar()}Hit",
                "Export best $l hit",
                "best${l}Hit"
            ) {
                it.meta.rootInfo.VJBase.geneIds[type].name
            }
        }

        if (withTargetFeatures) {
            val nFeatureField = Field(
                Order.`-nFeature`,
                "-nFeature",
                "Export nucleotide sequence of specified gene feature of specified node type.",
                baseGeneFeatureParam("nSeq"),
                nodeTypeParam("Of", withParent = false)
            ) { tree: SHMTreeForPostanalysis<*>, geneFeature: GeneFeature, what: Base ->
                when (what) {
                    Base.germline -> tree.root
                    Base.mrca -> tree.mrca
                    Base.parent -> throw UnsupportedOperationException()
                }
                    .targetNSequence(geneFeature)
                    ?.toString() ?: NULL
            }
            this += nFeatureField
            this += FieldsCollection(
                Order.`-nFeature` + 1,
                "-allNFeatures",
                "Export nucleotide sequences for all covered gene features.",
                nFeatureField,
                nodeTypeParam("Of", withParent = false)
            ) { base ->
                geneFeaturesBetween(null, null).map { it + base.name }
            }


            val aaFeatureField = Field(
                Order.`-aaFeature`,
                "-aaFeature",
                "Export amino acid sequence of specified gene feature of specified node type",
                baseGeneFeatureParam("aaSeq"),
                nodeTypeParam("Of", withParent = false)
            ) { tree: SHMTreeForPostanalysis<*>, geneFeature: GeneFeature, what: Base ->
                when (what) {
                    Base.germline -> tree.root
                    Base.mrca -> tree.mrca
                    Base.parent -> throw UnsupportedOperationException()
                }
                    .targetAASequence(geneFeature)
                    ?.toString() ?: NULL
            }
            this += aaFeatureField
            this += FieldsCollection(
                Order.`-aaFeature` + 1,
                "-allAaFeatures",
                "Export nucleotide sequences for all covered gene features.",
                aaFeatureField,
                nodeTypeParam("Of", withParent = false)
            ) { base ->
                geneFeaturesBetween(null, null).map { it + base.name }
            }
        }

        this += Field(
            Order.treeStats + 100,
            "-wildcardsScore",
            "Count of possible nucleotide sequences of CDR3 in MRCA",
            "wildcardsScore",
            deprecation = "-wildcardsScore used only for debug"
        ) { shmTree ->
            val CDR3Sequence = shmTree.mrca.targetNSequence(CDR3)!!
            val wildcardSized = (0 until CDR3Sequence.size())
                .map { CDR3Sequence.codeAt(it) }
                .filter { NucleotideSequence.ALPHABET.isWildcard(it) }
                .map { NucleotideSequence.ALPHABET.codeToWildcard(it) }
                .map { log2(it.basicSize().toDouble()) }
            wildcardSized.sum().toString()
        }
    }
}

private fun baseGeneFeatureParam(sPrefix: String): CommandArgRequired<GeneFeature> =
    CommandArgRequired(
        "<gene_feature>",
        { _, arg ->
            GeneFeature.parse(arg).also {
                require(!it.isComposite) {
                    "$cmdArgName doesn't support composite features"
                }
            }
        }
    ) { sPrefix + GeneFeature.encode(it) }
