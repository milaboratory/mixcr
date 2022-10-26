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
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.Base
import io.repseq.core.GeneFeature
import java.util.*

object SHMTreeNodeFieldsExtractor {
    fun nodeFields(): List<Field<SHMTreeForPostanalysis.SplittedNode>> = buildList {
        this += FieldParameterless(
            Order.treeNodeSpecific + 100,
            "-nodeId",
            "Node id in SHM tree",
            "nodeId"
        ) {
            it.id.toString()
        }

        this += FieldParameterless(
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
        this += FieldParameterless(
            Order.treeNodeSpecific + 200,
            "-parentId",
            "Parent node id in SHM tree",
            "parentId"
        ) {
            it.parentId?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.treeNodeSpecific + 300,
            "-distance",
            "Distance from another node",
            baseOnArg { base -> "DistanceFrom${base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}" }
        ) { node, base ->
            node.distanceFrom(base)?.toString() ?: NULL
        }

        this += FieldParameterless(
            Order.cloneSpecific + 50,
            "-fileName",
            "Name of clns file with sample",
            "fileName"
        ) {
            it.clone?.fileName ?: NULL
        }

        this += FieldWithParameters(
            Order.`-nFeature`,
            "-nFeature",
            "Export nucleotide sequence of specified gene feature.\n" +
                    "If second arg is 'node', then feature will be printed for current node. Otherwise - for corresponding ${Base.parent}, ${Base.germline} or ${Base.mrca}",
            baseGeneFeatureArg("nSeq"),
            baseOrArgOptional()
        ) { node, geneFeature, what ->
            node.mutationsFromGermlineTo(what)
                ?.targetNSequence(geneFeature)
                ?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.`-aaFeature`,
            "-aaFeature",
            "Export amino acid sequence of specified gene feature.\n" +
                    "If second arg is 'node', than feature will be printed for current node. Otherwise - for corresponding ${Base.parent}, ${Base.germline} or ${Base.mrca}",
            baseGeneFeatureArg("aaSeq"),
            baseOrArgOptional()
        ) { node, geneFeature, what ->
            node.mutationsFromGermlineTo(what)
                ?.targetAASequence(geneFeature)
                ?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.`-lengthOf`,
            "-lengthOf",
            "Export length of specified gene feature.\n" +
                    "If second arg is 'node', than feature length will be printed for current node. Otherwise - for corresponding ${Base.parent}, ${Base.germline} or ${Base.mrca}",
            baseGeneFeatureArg("lengthOf")
        ) { node, geneFeature ->
            node.mutationsFromGermline()
                .targetNSequence(geneFeature)?.size()
                ?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.`-nMutations`,
            "-nMutations",
            "Extract nucleotide mutations from specific node for specific gene feature.",
            baseGeneFeatureArg("nMutations"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkFeaturesForAlignment(feature)
            }
        ) { node, geneFeature, base ->
            node.mutationsFrom(base)
                ?.nAlignment(geneFeature)
                ?.absoluteMutations
                ?.encode() ?: "-"
        }

        this += FieldWithParameters(
            Order.`-nMutationsRelative`,
            "-nMutationsRelative",
            "Extract nucleotide mutations from specific node for specific gene feature relative to another feature.",
            baseGeneFeatureArg("nMutationsIn"),
            relativeGeneFeatureArg(),
            baseOnArg(),
            validateArgs = { feature, relativeTo, _ ->
                checkFeaturesForAlignment(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            node.mutationsFrom(base)
                ?.nAlignment(geneFeature, relativeTo)
                ?.absoluteMutations
                ?.encode() ?: "-"
        }


        this += FieldWithParameters(
            Order.`-aaMutations`,
            "-aaMutations",
            "Extract amino acid mutations from specific node for specific gene feature",
            baseGeneFeatureArg("aaMutations"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkFeaturesForAlignment(feature)
            }
        ) { node, geneFeature, base ->
            node.mutationsFrom(base)
                ?.aaAlignment(geneFeature)
                ?.absoluteMutations?.encode(",") ?: "-"
        }

        this += FieldWithParameters(
            Order.`-aaMutationsRelative`,
            "-aaMutationsRelative",
            "Extract amino acid mutations from specific node for specific gene feature relative to another feature.",
            baseGeneFeatureArg("aaMutations"),
            relativeGeneFeatureArg(),
            baseOnArg(),
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
        this += FieldWithParameters(
            Order.`-mutationsDetailed`,
            "-mutationsDetailed",
            "Detailed list of nucleotide and corresponding amino acid mutations from specific node. $detailedMutationsFormat",
            baseGeneFeatureArg("mutationsDetailedIn"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkFeaturesForAlignment(feature)
            }
        ) { node, geneFeature, base ->
            node.mutationsFrom(base)
                ?.aaMutationsDetailed(geneFeature)
                ?.encode(",") ?: "-"
        }

        this += FieldWithParameters(
            Order.`-mutationsDetailedRelative`,
            "-mutationsDetailedRelative",
            "Detailed list of nucleotide and corresponding amino acid mutations written, positions relative to specified gene feature. $detailedMutationsFormat",
            baseGeneFeatureArg("mutationsDetailedIn"),
            relativeGeneFeatureArg(),
            baseOnArg(),
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

private fun baseGeneFeatureArg(sPrefix: String): CommandArgRequired<GeneFeature> = CommandArgRequired(
    "<gene_feature>",
    { _, arg ->
        GeneFeature.parse(arg).also {
            require(!it.isComposite) {
                "$cmdArgName doesn't support composite features"
            }
        }
    }
) { sPrefix + GeneFeature.encode(it) }

private fun relativeGeneFeatureArg(): CommandArgRequired<GeneFeature> = CommandArgRequired(
    "<relative_to_gene_feature>",
    { _, arg ->
        GeneFeature.parse(arg).also {
            require(!it.isComposite) {
                "$cmdArgName doesn't support composite features"
            }
        }
    }
) { "Relative" + GeneFeature.encode(it) }

private fun baseOnArg(
    sPrefix: (Base) -> String = { base -> "BasedOn${base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}" }
): CommandArgRequired<Base> = CommandArgRequired(
    "<${Base.germline}|${Base.mrca}|${Base.parent}>",
    { _, arg ->
        require(arg in arrayOf(Base.germline.name, Base.mrca.name, Base.germline.name)) {
            "$cmdArgName: unexpected arg $arg, expecting ${Base.germline} or ${Base.mrca}"
        }
        Base.valueOf(arg)
    },
    sPrefix
)

private fun baseOrArgOptional(
    sPrefix: (Base?) -> String = { base -> "of${(base?.name ?: "node").replaceFirstChar { it.titlecase(Locale.getDefault()) }}" }
): CommandArgOptional<Base?> = CommandArgOptional(
    "<${Base.germline}|${Base.mrca}|${Base.parent}>",
    { arg ->
        arg in arrayOf(Base.germline.name, Base.mrca.name, Base.germline.name, "node")
    },
    { _, arg ->
        require(arg in arrayOf(Base.germline.name, Base.mrca.name, Base.germline.name, "node")) {
            "$cmdArgName: unexpected arg $arg, expecting ${Base.germline} or ${Base.mrca}"
        }
        when (arg) {
            "node" -> null
            else -> Base.valueOf(arg)
        }
    },
    sPrefix
)

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
