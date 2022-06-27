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
import com.milaboratory.mixcr.export.FieldExtractors.NULL
import com.milaboratory.mixcr.export.FieldWithParameters.CommandArg
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.Base
import io.repseq.core.GeneFeature
import java.util.*

object SHNTreeNodeFieldsExtractor : BaseFieldExtractors() {
    override fun initFields(): Array<Field<out Any>> {
        val fields = mutableListOf<Field<SHMTreeForPostanalysis.SplittedNode>>()

        fields += FieldParameterless(
            "-nodeId",
            "Node id in SHM tree",
            "Node id",
            "nodeId"
        ) {
            it.id.toString()
        }

        fields += FieldParameterless(
            "-fileName",
            "Name of clns file with sample",
            "File name",
            "fileName"
        ) {
            it.clone?.fileName ?: ""
        }

        fields += FieldWithParameters(
            "-distance",
            "Distance from another node",
            baseOnArg(
                hPrefix = { "Distance from $it" },
                sPrefix = { base -> "DistanceFrom${base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}" }
            )
        ) { node, base ->
            node.distanceFrom(base)?.toString() ?: ""
        }

        fields += FieldWithParameters(
            "-nFeature",
            "Export nucleotide sequence of specified gene feature",
            baseGeneFeatureArg("N. Seq. ", "nSeq"),
            validateArgs = { checkNotComposite(it) }
        ) { node, geneFeature ->
            val mutationsWithRange = node.mutationsDescription(geneFeature) ?: return@FieldWithParameters NULL
            mutationsWithRange.targetNSequence.toString()
        }

        fields += FieldWithParameters(
            "-aaFeature",
            "Export amino acid sequence of specified gene feature",
            baseGeneFeatureArg("AA. Seq. ", "aaSeq"),
            validateArgs = { checkNotComposite(it) }
        ) { node, geneFeature ->
            val mutationsWithRange = node.mutationsDescription(geneFeature) ?: return@FieldWithParameters NULL
            mutationsWithRange.targetAASequence.toString()
        }

        fields += FieldWithParameters(
            "-nMutations",
            "Extract nucleotide mutations for specific gene feature; relative to germline sequence.",
            baseGeneFeatureArg("N. Mutations in ", "nMutations"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkNotComposite(feature)
            }
        ) { node, geneFeature, base ->
            val mutationsWithRange =
                node.mutationsDescription(geneFeature, baseOn = base) ?: return@FieldWithParameters "-"
            mutationsWithRange.mutations.encode()
        }

        fields += FieldWithParameters(
            "-nMutationsRelative",
            "Extract nucleotide mutations for specific gene feature relative to another feature.",
            baseGeneFeatureArg("N. Mutations in ", "nMutationsIn"),
            relativeGeneFeatureArg(),
            baseOnArg(),
            validateArgs = { feature, relativeTo, _ ->
                checkNotComposite(feature)
                checkRelativeFeatures(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            val mutationsWithRange =
                node.mutationsDescription(geneFeature, relativeTo, base) ?: return@FieldWithParameters "-"
            mutationsWithRange.mutations.encode()
        }


        fields += FieldWithParameters(
            "-aaMutations",
            "Extract amino acid mutations for specific gene feature",
            baseGeneFeatureArg("AA. Mutations in ", "aaMutations"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkNotComposite(feature)
            }
        ) { node, geneFeature, base ->
            val mutationsWithRange =
                node.mutationsDescription(geneFeature, baseOn = base) ?: return@FieldWithParameters "-"
            mutationsWithRange.aaMutations.encode(",")
        }

        fields += FieldWithParameters(
            "-aaMutationsRelative",
            "Extract amino acid mutations for specific gene feature relative to another feature.",
            baseGeneFeatureArg("AA. Mutations in ", "aaMutations"),
            relativeGeneFeatureArg(),
            baseOnArg(),
            validateArgs = { feature, relativeTo, _ ->
                checkNotComposite(feature)
                checkRelativeFeatures(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            val mutationsWithRange =
                node.mutationsDescription(geneFeature, relativeTo, base) ?: return@FieldWithParameters "-"
            mutationsWithRange.aaMutations.encode(",")
        }

        val detailedMutationsFormat =
            "Format <nt_mutation>:<aa_mutation_individual>:<aa_mutation_cumulative>, where <aa_mutation_individual> is an expected amino acid " +
                "mutation given no other mutations have occurred, and <aa_mutation_cumulative> amino acid mutation is the observed amino acid " +
                "mutation combining effect from all other. WARNING: format may change in following versions."
        fields += FieldWithParameters(
            "-mutationsDetailed",
            "Detailed list of nucleotide and corresponding amino acid mutations. $detailedMutationsFormat",
            baseGeneFeatureArg("Detailed mutations in ", "mutationsDetailedIn"),
            baseOnArg(),
            validateArgs = { feature, _ ->
                checkNotComposite(feature)
            }
        ) { node, geneFeature, base ->
            val mutationsWithRange =
                node.mutationsDescription(geneFeature, baseOn = base) ?: return@FieldWithParameters "-"
            mutationsWithRange.aaMutationsDetailed.encode(",")
        }

        fields += FieldWithParameters(
            "-mutationsDetailedRelative",
            "Detailed list of nucleotide and corresponding amino acid mutations written, positions relative to specified gene feature. $detailedMutationsFormat",
            baseGeneFeatureArg("Detailed mutations in ", "mutationsDetailedIn"),
            relativeGeneFeatureArg(),
            baseOnArg(),
            validateArgs = { feature, relativeTo, _ ->
                checkNotComposite(feature)
                checkRelativeFeatures(feature, relativeTo)
            }
        ) { node, geneFeature, relativeTo, base ->
            val mutationsWithRange =
                node.mutationsDescription(geneFeature, relativeTo, base) ?: return@FieldWithParameters "-"
            mutationsWithRange.aaMutationsDetailed.encode(",")
        }

        return fields.toTypedArray()
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

    private fun AbstractField<*>.checkRelativeFeatures(
        feature: GeneFeature,
        relativeTo: GeneFeature
    ) {
        require(relativeTo in feature) {
            String.format(
                "%s: Base feature %s does not contain relative feature %s",
                command, GeneFeature.encode(feature), GeneFeature.encode(relativeTo)
            )
        }
        arrayOf(feature, relativeTo).forEach {
            requireNotNull(it.geneType) {
                String.format(
                    "%s: Gene feature %s covers several gene types " +
                        "(not possible to select corresponding alignment)", command, GeneFeature.encode(it)
                )
            }
        }

        require(!relativeTo.isAlignmentAttached) {
            String.format(
                "%s: Alignment attached base gene features not allowed (error in %s)",
                command, GeneFeature.encode(relativeTo)
            )
        }
    }

    private fun checkNotComposite(feature: GeneFeature) {
        require(!feature.isComposite) {
            "Command doesn't support composite features"
        }
    }

}

private fun Array<MutationsUtil.MutationNt2AADescriptor>.encode(separator: String): String =
    joinToString(separator) { it.toString() }
