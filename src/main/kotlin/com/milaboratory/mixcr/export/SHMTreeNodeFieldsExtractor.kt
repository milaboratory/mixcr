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
import com.milaboratory.mixcr.export.FieldWithParameters.CommandArg
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
            "Node id",
            "nodeId"
        ) {
            it.id.toString()
        }

        this += FieldParameterless(
            Order.treeNodeSpecific + 200,
            "-parentId",
            "Parent node id in SHM tree",
            "Parent id",
            "parentId"
        ) {
            it.parentId?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.treeNodeSpecific + 300,
            "-distance",
            "Distance from another node",
            baseOnArg(
                hPrefix = { "Distance from $it" },
                sPrefix = { base -> "DistanceFrom${base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}" }
            )
        ) { node, base ->
            node.distanceFrom(base)?.toString() ?: NULL
        }

        this += FieldParameterless(
            Order.cloneSpecific + 50,
            "-fileName",
            "Name of clns file with sample",
            "File name",
            "fileName"
        ) {
            it.clone?.fileName ?: NULL
        }

        this += FieldWithParameters(
            Order.`-nFeature`,
            "-nFeature",
            "Export nucleotide sequence of specified gene feature",
            baseGeneFeatureArg("N. Seq. ", "nSeq"),
            validateArgs = {
                checkNotComposite(it)
            }
        ) { node, geneFeature ->
            node.mutationsDescription()
                .targetNSequence(geneFeature)
                ?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.`-aaFeature`,
            "-aaFeature",
            "Export amino acid sequence of specified gene feature",
            baseGeneFeatureArg("AA. Seq. ", "aaSeq"),
            validateArgs = {
                checkNotComposite(it)
            }
        ) { node, geneFeature ->
            node.mutationsDescription()
                .targetAASequence(geneFeature)
                ?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.`-lengthOf`,
            "-lengthOf",
            "Export length of specified gene feature.",
            baseGeneFeatureArg("Length of ", "lengthOf"),
            validateArgs = {
                checkNotComposite(it)
            }
        ) { node, geneFeature ->
            node.mutationsDescription()
                .targetNSequence(geneFeature)?.size()
                ?.toString() ?: NULL
        }

        this += FieldWithParameters(
            Order.`-nMutations`,
            "-nMutations",
            "Extract nucleotide mutations for specific gene feature.",
            baseGeneFeatureArg("N. Mutations in ", "nMutations"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkNotComposite(feature)
                checkFeaturesForAlignment(feature)
            }
        ) { node, geneFeature, base ->
            node.mutationsDescription(base)
                ?.nAlignment(geneFeature)
                ?.absoluteMutations
                ?.encode() ?: "-"
        }

        this += FieldWithParameters(
            Order.`-nMutationsRelative`,
            "-nMutationsRelative",
            "Extract nucleotide mutations for specific gene feature relative to another feature.",
            baseGeneFeatureArg("N. Mutations in ", "nMutationsIn"),
            relativeGeneFeatureArg(),
            baseOnArg(),
            validateArgs = { feature, relativeTo, _ ->
                checkNotComposite(feature)
                checkFeaturesForAlignment(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            node.mutationsDescription(base)
                ?.nAlignment(geneFeature, relativeTo)
                ?.absoluteMutations
                ?.encode() ?: "-"
        }


        this += FieldWithParameters(
            Order.`-aaMutations`,
            "-aaMutations",
            "Extract amino acid mutations for specific gene feature",
            baseGeneFeatureArg("AA. Mutations in ", "aaMutations"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkNotComposite(feature)
                checkFeaturesForAlignment(feature)
            }
        ) { node, geneFeature, base ->
            node.mutationsDescription(base)
                ?.aaAlignment(geneFeature)
                ?.absoluteMutations?.encode(",") ?: "-"
        }

        this += FieldWithParameters(
            Order.`-aaMutationsRelative`,
            "-aaMutationsRelative",
            "Extract amino acid mutations for specific gene feature relative to another feature.",
            baseGeneFeatureArg("AA. Mutations in ", "aaMutations"),
            relativeGeneFeatureArg(),
            baseOnArg(),
            validateArgs = { feature, relativeTo, _ ->
                checkNotComposite(feature)
                checkFeaturesForAlignment(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            node.mutationsDescription(base)
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
            "Detailed list of nucleotide and corresponding amino acid mutations. $detailedMutationsFormat",
            baseGeneFeatureArg("Detailed mutations in ", "mutationsDetailedIn"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkNotComposite(feature)
                checkFeaturesForAlignment(feature)
            }
        ) { node, geneFeature, base ->
            node.mutationsDescription(base)
                ?.aaMutationsDetailed(geneFeature)
                ?.encode(",") ?: "-"
        }

        this += FieldWithParameters(
            Order.`-mutationsDetailedRelative`,
            "-mutationsDetailedRelative",
            "Detailed list of nucleotide and corresponding amino acid mutations written, positions relative to specified gene feature. $detailedMutationsFormat",
            baseGeneFeatureArg("Detailed mutations in ", "mutationsDetailedIn"),
            relativeGeneFeatureArg(),
            baseOnArg(),
            validateArgs = { feature, relativeTo, _ ->
                checkNotComposite(feature)
                checkFeaturesForAlignment(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            node.mutationsDescription(base)
                ?.aaMutationsDetailed(geneFeature, relativeTo)
                ?.encode(",") ?: "-"
        }
    }
}

private fun baseGeneFeatureArg(hPrefix: String, sPrefix: String): CommandArg<GeneFeature> = CommandArg(
    "<gene_feature>",
    { GeneFeature.parse(it) },
    { hPrefix + GeneFeature.encode(it) },
    { sPrefix + GeneFeature.encode(it) }
)

private fun relativeGeneFeatureArg(): CommandArg<GeneFeature> = CommandArg(
    "<relative_to_gene_feature>",
    { GeneFeature.parse(it) },
    { "relative to " + GeneFeature.encode(it) },
    { "Relative" + GeneFeature.encode(it) }
)

private fun baseOnArg(
    hPrefix: (Base) -> String = { "based on $it" },
    sPrefix: (Base) -> String = { base -> "BasedOn${base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}" }
): CommandArg<Base> = CommandArg(
    "<${Base.germline}|${Base.mrca}|${Base.parent}>",
    { Base.valueOf(it) },
    hPrefix,
    sPrefix
)

private fun AbstractField<*>.checkFeaturesForAlignment(
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

private fun AbstractField<*>.checkNotComposite(feature: GeneFeature) {
    require(!feature.isComposite) {
        "$cmdArgName doesn't support composite features"
    }
}

private fun Array<MutationsUtil.MutationNt2AADescriptor>.encode(separator: String): String =
    joinToString(separator) { it.toString() }
