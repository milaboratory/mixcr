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

import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.mixcr.export.FieldExtractorsFactory.Order
import com.milaboratory.mixcr.export.GeneFeaturesRangeUtil.geneFeaturesBetween
import com.milaboratory.mixcr.export.ParametersFactory.nodeTypeParam
import com.milaboratory.mixcr.export.ParametersFactory.nodeTypeParamOptional
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.Base
import io.repseq.core.GeneFeature

object SHMTreeNodeFieldsExtractor {
    private fun nodeParamDescription(subject: String) =
        "If second arg is omitted, than $subject will be printed for current node. Otherwise - for corresponding ${Base.parent}, ${Base.germline} or ${Base.mrca}"

    fun nodeFields(): List<FieldsCollection<SHMTreeForPostanalysis.SplittedNode>> = buildList {
        this += Field(
            Order.treeNodeSpecific + 100,
            "-nodeId",
            "Node id in SHM tree",
            "nodeId"
        ) {
            it.id.toString()
        }

        this += Field(
            Order.treeNodeSpecific + 150,
            "-isObserved",
            "Is node have clones. All other nodes are reconstructed by algorithm",
            "isObserved"
        ) {
            when {
                it.clone != null -> "true"
                else -> "false"
            }
        }
        this += Field(
            Order.treeNodeSpecific + 200,
            "-parentId",
            "Parent node id in SHM tree",
            "parentId"
        ) {
            it.parentId?.toString() ?: NULL
        }

        this += Field(
            Order.treeNodeSpecific + 300,
            "-distance",
            "Distance from another node",
            nodeTypeParam("DistanceFrom")
        ) { node, base ->
            node.distanceFrom(base)?.toString() ?: NULL
        }

        this += Field(
            Order.cloneSpecific + 50,
            "-fileName",
            "Name of clns file with sample",
            "fileName"
        ) {
            it.clone?.fileName ?: NULL
        }

        val nFeatureField = Field(
            Order.`-nFeature`,
            "-nFeature",
            "Export nucleotide sequence of specified gene feature.%n${nodeParamDescription("feature")}",
            baseGeneFeatureParam("nSeq"),
            nodeTypeParamOptional("Of")
        ) { node: SHMTreeForPostanalysis.SplittedNode, geneFeature: GeneFeature, what: Base? ->
            node.mutationsFromGermlineTo(what)
                ?.targetNSequence(geneFeature)
                ?.toString() ?: NULL
        }
        this += nFeatureField
        this += FieldsCollection(
            Order.`-nFeature` + 1,
            "-allNFeatures",
            "Export nucleotide sequences for all covered gene features.%n${nodeParamDescription("feature")}",
            nFeatureField,
            nodeTypeParamOptional("Of")
        ) { base ->
            val geneFeaturesBetween = geneFeaturesBetween(null, null)
            when {
                base != null -> geneFeaturesBetween.map { it + base.name }
                else -> geneFeaturesBetween
            }
        }


        val aaFeatureField = Field(
            Order.`-aaFeature`,
            "-aaFeature",
            "Export amino acid sequence of specified gene feature.%n${nodeParamDescription("feature")}",
            baseGeneFeatureParam("aaSeq"),
            nodeTypeParamOptional("Of")
        ) { node: SHMTreeForPostanalysis.SplittedNode, geneFeature: GeneFeature, what: Base? ->
            node.mutationsFromGermlineTo(what)
                ?.targetAASequence(geneFeature)
                ?.toString() ?: NULL
        }
        this += aaFeatureField
        this += FieldsCollection(
            Order.`-aaFeature` + 1,
            "-allAaFeatures",
            "Export amino acid sequences for all covered gene features.%n${nodeParamDescription("feature")}",
            aaFeatureField,
            nodeTypeParamOptional("Of")
        ) { base ->
            val geneFeaturesBetween = geneFeaturesBetween(null, null)
            when {
                base != null -> geneFeaturesBetween.map { it + base.name }
                else -> geneFeaturesBetween
            }
        }

        val lengthOfField = Field(
            Order.`-lengthOf`,
            "-lengthOf",
            "Export length of specified gene feature.%n${nodeParamDescription("length")}",
            baseGeneFeatureParam("lengthOf"),
            nodeTypeParamOptional("Of")
        ) { node: SHMTreeForPostanalysis.SplittedNode, geneFeature: GeneFeature, what: Base? ->
            node.mutationsFromGermlineTo(what)
                ?.targetNSequence(geneFeature)?.size()
                ?.toString() ?: NULL
        }
        this += lengthOfField
        this += FieldsCollection(
            Order.`-lengthOf` + 1,
            "-allLengthOf",
            "Export lengths for all covered gene features.%n${nodeParamDescription("feature")}",
            lengthOfField,
            nodeTypeParamOptional("Of")
        ) { base ->
            val geneFeaturesBetween = geneFeaturesBetween(null, null)
            when {
                base != null -> geneFeaturesBetween.map { it + base.name }
                else -> geneFeaturesBetween
            }
        }

        val nMutationsField = Field(
            Order.`-nMutations`,
            "-nMutations",
            "Extract nucleotide mutations from specific node for specific gene feature.",
            baseGeneFeatureParam("nMutations"),
            nodeTypeParam("BasedOn"),
            validateArgs = { feature, _ ->
                checkFeaturesForAlignment(feature)
            }
        ) { node: SHMTreeForPostanalysis.SplittedNode, geneFeature: GeneFeature, base: Base ->
            node.mutationsFrom(base)
                ?.nAlignment(geneFeature)
                ?.absoluteMutations
                ?.encode() ?: "-"
        }
        this += nMutationsField
        this += FieldsCollection(
            Order.`-nMutations` + 1,
            "-allNMutations",
            "Extract nucleotide mutations from specific node for all covered gene features.",
            nMutationsField,
            nodeTypeParam("BasedOn")
        ) { base ->
            geneFeaturesBetween(null, null).map { it + base.name }
        }

        this += Field(
            Order.`-nMutationsRelative`,
            "-nMutationsRelative",
            "Extract nucleotide mutations from specific node for specific gene feature relative to another feature.",
            baseGeneFeatureParam("nMutationsIn"),
            relativeGeneFeatureParam(),
            nodeTypeParam("BasedOn"),
            validateArgs = { feature, relativeTo, _ ->
                checkFeaturesForAlignment(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            node.mutationsFrom(base)
                ?.nAlignment(geneFeature, relativeTo)
                ?.absoluteMutations
                ?.encode() ?: "-"
        }


        val aaMutationsField = Field(
            Order.`-aaMutations`,
            "-aaMutations",
            "Extract amino acid mutations from specific node for specific gene feature",
            baseGeneFeatureParam("aaMutations"),
            nodeTypeParam("BasedOn"),
            validateArgs = { feature, _ ->
                checkFeaturesForAlignment(feature)
            }
        ) { node: SHMTreeForPostanalysis.SplittedNode, geneFeature: GeneFeature, base: Base ->
            node.mutationsFrom(base)
                ?.aaAlignment(geneFeature)
                ?.absoluteMutations?.encode(",") ?: "-"
        }
        this += aaMutationsField
        this += FieldsCollection(
            Order.`-aaMutations` + 1,
            "-allAaMutations",
            "Extract amino acid mutations from specific node for all covered gene features.",
            aaMutationsField,
            nodeTypeParam("BasedOn")
        ) { base ->
            geneFeaturesBetween(null, null).map { it + base.name }
        }

        this += Field(
            Order.`-aaMutationsRelative`,
            "-aaMutationsRelative",
            "Extract amino acid mutations from specific node for specific gene feature relative to another feature.",
            baseGeneFeatureParam("aaMutations"),
            relativeGeneFeatureParam(),
            nodeTypeParam("BasedOn"),
            validateArgs = { feature, relativeTo, _ ->
                checkFeaturesForAlignment(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            node.mutationsFrom(base)
                ?.aaAlignment(geneFeature, relativeTo)
                ?.absoluteMutations?.encode(",") ?: "-"
        }

        val detailedMutationsFormat =
            "Format <nt_mutation>:<aa_mutation_individual>:<aa_mutation_cumulative>, where <aa_mutation_individual> is an expected amino acid " +
                    "mutation given no other mutations have occurred, and <aa_mutation_cumulative> amino acid mutation is the observed amino acid " +
                    "mutation combining effect from all other. WARNING: format may change in following versions."
        val mutationsDetailedField = Field(
            Order.`-mutationsDetailed`,
            "-mutationsDetailed",
            "Detailed list of nucleotide and corresponding amino acid mutations from specific node. $detailedMutationsFormat",
            baseGeneFeatureParam("mutationsDetailedIn"),
            nodeTypeParam("BasedOn"),
            validateArgs = { feature, _ ->
                checkFeaturesForAlignment(feature)
            }
        ) { node: SHMTreeForPostanalysis.SplittedNode, geneFeature: GeneFeature, base: Base ->
            node.mutationsFrom(base)
                ?.aaMutationsDetailed(geneFeature)
                ?.encode(",") ?: "-"
        }
        this += mutationsDetailedField
        this += FieldsCollection(
            Order.`-mutationsDetailed` + 1,
            "-allMutationsDetailed",
            "Detailed list of nucleotide and corresponding amino acid mutations from specific node for all covered gene features.",
            mutationsDetailedField,
            nodeTypeParam("BasedOn")
        ) { base ->
            geneFeaturesBetween(null, null).map { it + base.name }
        }

        this += Field(
            Order.`-mutationsDetailedRelative`,
            "-mutationsDetailedRelative",
            "Detailed list of nucleotide and corresponding amino acid mutations written, positions relative to specified gene feature. $detailedMutationsFormat",
            baseGeneFeatureParam("mutationsDetailedIn"),
            relativeGeneFeatureParam(),
            nodeTypeParam("BasedOn"),
            validateArgs = { feature, relativeTo, _ ->
                checkFeaturesForAlignment(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            node.mutationsFrom(base)
                ?.aaMutationsDetailed(geneFeature, relativeTo)
                ?.encode(",") ?: "-"
        }
    }
}

private fun baseGeneFeatureParam(sPrefix: String): CommandArgRequired<GeneFeature> = CommandArgRequired(
    "<gene_feature>",
    { _, arg ->
        GeneFeature.parse(arg).also {
            require(!it.isComposite) {
                "$cmdArgName doesn't support composite features"
            }
        }
    }
) { sPrefix + GeneFeature.encode(it) }

private fun relativeGeneFeatureParam(): CommandArgRequired<GeneFeature> = CommandArgRequired(
    "<relative_to_gene_feature>",
    { _, arg ->
        GeneFeature.parse(arg).also {
            require(!it.isComposite) {
                "$cmdArgName doesn't support composite features"
            }
        }
    }
) { "Relative" + GeneFeature.encode(it) }

private fun Field<*>.checkFeaturesForAlignment(
    feature: GeneFeature,
    relativeTo: GeneFeature = feature
) {
    if (feature != relativeTo) {
        listOfNotNull(feature, relativeTo).forEach {
            requireNotNull(it.geneType) {
                "$cmdArgName: Gene feature ${GeneFeature.encode(it)} covers several gene types (not possible to select corresponding alignment)"
            }
        }
    }

    require(!feature.isAlignmentAttached) {
        "$cmdArgName: Alignment attached base gene features not allowed (error in ${GeneFeature.encode(feature)})"
    }
}

private fun Array<MutationsUtil.MutationNt2AADescriptor>.encode(separator: String): String =
    joinToString(separator) { it.toString() }
