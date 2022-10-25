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

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.TranslationParameters
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.VDJCObject
import com.milaboratory.mixcr.export.FieldExtractorsFactory.FieldCommandArgs
import com.milaboratory.mixcr.export.FieldExtractorsFactory.Order
import gnu.trove.map.hash.TObjectFloatHashMap
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.SequencePartitioning
import java.text.DecimalFormat
import java.util.*

private val SCORE_FORMAT = DecimalFormat("#.#")
private const val MAX_SHIFTED_TRIPLETS = 3

object VDJCObjectFieldExtractors {
    val presets: Map<String, List<FieldCommandArgs>> = buildMap {
        this["min"] = listOf(
            FieldCommandArgs("-vHit"),
            FieldCommandArgs("-dHit"),
            FieldCommandArgs("-jHit"),
            FieldCommandArgs("-cHit"),
            FieldCommandArgs("-nFeature", "CDR3")
        )

        this["full"] = listOf(
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

        this["fullImputed"] = this.getValue("full").map { fieldData ->
            when (fieldData.field) {
                "-nFeature" -> FieldCommandArgs("-nFeatureImputed", fieldData.args)
                "-aaFeature" -> FieldCommandArgs("-aaFeatureImputed", fieldData.args)
                else -> fieldData
            }
        }
    }

    fun vdjcObjectFields(forTreesExport: Boolean): List<Field<VDJCObject>> = buildList {
        // Number of targets
        this += FieldParameterless(
            Order.targetsCount + 100,
            "-targets",
            "Export number of targets",
            "Number of targets",
            "numberOfTargets"
        ) { vdjcObject: VDJCObject -> vdjcObject.numberOfTargets().toString() }

        // Best hits
        GeneType.values().forEach { type ->
            if (!forTreesExport || type !in GeneType.VJ_REFERENCE) {
                val l = type.letter
                this += FieldParameterless(
                    Order.orderForBestHit(type),
                    "-${l.lowercaseChar()}Hit",
                    "Export best $l hit",
                    "Best $l hit",
                    "best${l}Hit"
                ) { vdjcObject: VDJCObject ->
                    val bestHit = vdjcObject.getBestHit(type) ?: return@FieldParameterless NULL
                    bestHit.gene.name
                }
            }
        }

        // Best gene
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.hits + 200 + index,
                "-${l.lowercaseChar()}Gene",
                "Export best $l hit gene name (e.g. TRBV12-3 for TRBV12-3*00)",
                "Best $l gene",
                "best${l}Gene"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@FieldParameterless NULL
                bestHit.gene.geneName
            }
        }

        // Best family
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.hits + 300 + index,
                "-${l.lowercaseChar()}Family",
                "Export best $l hit family name (e.g. TRBV12 for TRBV12-3*00)",
                "Best $l family",
                "best${l}Family"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@FieldParameterless NULL
                bestHit.gene.familyName
            }
        }

        // Best hit score
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.hits + 400 + index,
                "-${l.lowercaseChar()}HitScore",
                "Export score for best $l hit",
                "Best $l hit score",
                "best${l}HitScore"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@FieldParameterless NULL
                bestHit.score.toString()
            }
        }

        // All hits
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.hits + 500 + index,
                "-${l.lowercaseChar()}HitsWithScore",
                "Export all $l hits with score",
                "All $l hits with score",
                "all${l}HitsWithScore"
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@FieldParameterless NULL
                val sb = StringBuilder()
                var i = 0
                while (true) {
                    sb.append(hits[i].gene.name)
                        .append("(").append(SCORE_FORMAT.format(hits[i].score.toDouble()))
                        .append(")")
                    if (i == hits.size - 1) break
                    sb.append(",")
                    i++
                }
                sb.toString()
            }
        }

        // All hits without score
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.hits + 600 + index,
                "-${l.lowercaseChar()}Hits",
                "Export all $l hits",
                "All $l Hits",
                "all${l}Hits"
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@FieldParameterless NULL
                val sb = StringBuilder()
                var i = 0
                while (true) {
                    sb.append(hits[i].gene.name)
                    if (i == hits.size - 1) break
                    sb.append(",")
                    i++
                }
                sb.toString()
            }
        }

        // All gene names
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.hits + 700 + index,
                "-${l.lowercaseChar()}Genes",
                "Export all $l gene names (e.g. TRBV12-3 for TRBV12-3*00)",
                "All $l genes",
                "all${l}Genes"
            ) { vdjcObject: VDJCObject ->
                vdjcObject.hitsDescription(type) { it.gene.geneName }
            }
        }

        // All families
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.hits + 800 + index,
                "-${l.lowercaseChar()}Families",
                "Export all $l gene family anmes (e.g. TRBV12 for TRBV12-3*00)",
                "All $l families",
                "all${l}Families",
            ) { vdjcObject: VDJCObject ->
                vdjcObject.hitsDescription(type) { it.gene.familyName }
            }
        }

        // Best alignment
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.alignments + 100 + index,
                "-${l.lowercaseChar()}Alignment",
                "Export best $l alignment",
                "Best $l alignment",
                "best${l}Alignment"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@FieldParameterless NULL
                val sb = StringBuilder()
                var i = 0
                while (true) {
                    val alignment = bestHit.getAlignment(i)
                    if (alignment == null) sb.append(NULL) else sb.append(alignment.toCompactString())
                    if (i == vdjcObject.numberOfTargets() - 1) break
                    sb.append(",")
                    i++
                }
                sb.toString()
            }
        }

        // All alignments
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += FieldParameterless(
                Order.alignments + 200 + index,
                "-${l.lowercaseChar()}Alignments",
                "Export all $l alignments",
                "All $l alignments",
                "all${l}Alignments"
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@FieldParameterless NULL
                val sb = StringBuilder()
                var j = 0
                while (true) {
                    var i = 0
                    while (true) {
                        val alignment = hits[j].getAlignment(i)
                        if (alignment == null) sb.append(NULL) else sb.append(alignment.toCompactString())
                        if (i == vdjcObject.numberOfTargets() - 1) break
                        sb.append(',')
                        i++
                    }
                    if (j == hits.size - 1) break
                    sb.append(';')
                    ++j
                }
                sb.toString()
            }
        }
        if (!forTreesExport) {
            this += FieldWithParameters(
                Order.`-nFeature`,
                "-nFeature",
                "Export nucleotide sequence of specified gene feature",
                geneFeatureParam("N. Seq. ", "nSeq")
            ) { vdjcObject: VDJCObject, feature ->
                vdjcObject.getFeature(feature)?.sequence?.toString() ?: NULL
            }
        }
        this += FieldWithParameters(
            Order.features + 200,
            "-qFeature",
            "Export quality string of specified gene feature",
            geneFeatureParam("Qual. ", "qual")
        ) { vdjcObject: VDJCObject, feature ->
            vdjcObject.getFeature(feature)?.quality?.toString() ?: NULL
        }

        if (!forTreesExport) {
            this += FieldWithParameters(
                Order.`-aaFeature`,
                "-aaFeature",
                "Export amino acid sequence of specified gene feature",
                geneFeatureParam("AA. Seq. ", "aaSeq")
            ) { vdjcObject: VDJCObject, geneFeature ->
                val feature = vdjcObject.getFeature(geneFeature) ?: return@FieldWithParameters NULL
                val targetId = vdjcObject.getTargetContainingFeature(geneFeature)
                val tr = if (targetId == -1) {
                    TranslationParameters.FromLeftWithIncompleteCodon
                } else {
                    vdjcObject.getPartitionedTarget(targetId).partitioning.getTranslationParameters(geneFeature)
                }
                if (tr == null) return@FieldWithParameters NULL
                AminoAcidSequence.translate(feature.sequence, tr).toString()
            }
        }
        this += FieldWithParameters(
            Order.features + 400,
            "-nFeatureImputed",
            "Export nucleotide sequence of specified gene feature using letters from germline (marked lowercase) for uncovered regions",
            geneFeatureParam("N. Inc. Seq. ", "nSeqImputed")
        ) { vdjcObject: VDJCObject, geneFeature ->
            vdjcObject.getIncompleteFeature(geneFeature)?.toString() ?: NULL
        }
        this += FieldWithParameters(
            Order.features + 500,
            "-aaFeatureImputed",
            "Export amino acid sequence of specified gene feature using letters from germline (marked lowercase) for uncovered regions",
            geneFeatureParam("AA. Inc. Seq. ", "aaSeqImputed")
        ) { vdjcObject: VDJCObject, geneFeature ->
            vdjcObject.getIncompleteFeature(geneFeature)?.toAminoAcidString() ?: NULL
        }
        this += FieldWithParameters(
            Order.features + 600,
            "-minFeatureQuality",
            "Export minimal quality of specified gene feature",
            geneFeatureParam("Min. qual. ", "minQual")
        ) { vdjcObject: VDJCObject, feature ->
            vdjcObject.getFeature(feature)?.quality?.minValue()?.toString() ?: NULL
        }
        this += FieldWithParameters(
            Order.features + 700,
            "-avrgFeatureQuality",
            "Export average quality of specified gene feature",
            geneFeatureParam("Mean. qual. ", "meanQual")
        ) { vdjcObject: VDJCObject, feature ->
            vdjcObject.getFeature(feature)?.quality?.meanValue()?.toString() ?: NULL
        }
        if (!forTreesExport) {
            this += FieldWithParameters(
                Order.`-lengthOf`,
                "-lengthOf",
                "Export length of specified gene feature.",
                geneFeatureParam("Length of ", "lengthOf")
            ) { vdjcObject: VDJCObject, feature ->
                vdjcObject.getFeature(feature)?.size()?.toString() ?: NULL
            }
            this += FieldWithParameters(
                Order.`-nMutations`,
                "-nMutations",
                "Extract nucleotide mutations for specific gene feature; relative to germline sequence.",
                geneFeatureParam("N. Mutations in ", "nMutations"),
                { geneFeature -> validateFeaturesForMutationsExtraction(geneFeature) }
            ) { obj: VDJCObject, geneFeature ->
                obj.nMutations(geneFeature)?.encode(",") ?: "-"
            }
            this += FieldWithParameters(
                Order.`-nMutationsRelative`,
                "-nMutationsRelative",
                "Extract nucleotide mutations for specific gene feature relative to another feature.",
                geneFeatureParam("N. Mutations in ", "nMutationsIn"),
                relativeGeneFeatureParam(),
                { geneFeature, relativeTo -> validateFeaturesForMutationsExtraction(geneFeature, relativeTo) }
            ) { obj: VDJCObject, geneFeature, relativeTo ->
                obj.nMutations(geneFeature, relativeTo)?.encode(",") ?: "-"
            }
            this += FieldWithParameters(
                Order.`-aaMutations`,
                "-aaMutations",
                "Extract amino acid mutations for specific gene feature",
                geneFeatureParam("AA. Mutations in ", "aaMutations"),
                { geneFeature -> validateFeaturesForMutationsExtraction(geneFeature) }
            ) { obj: VDJCObject, geneFeature ->
                obj.aaMutations(geneFeature)?.encode(",") ?: "-"
            }
            this += FieldWithParameters(
                Order.`-aaMutationsRelative`,
                "-aaMutationsRelative",
                "Extract amino acid mutations for specific gene feature relative to another feature.",
                geneFeatureParam("AA. Mutations in ", "aaMutationsIn"),
                relativeGeneFeatureParam(),
                { geneFeature, relativeTo -> validateFeaturesForMutationsExtraction(geneFeature, relativeTo) }
            ) { obj: VDJCObject, geneFeature, relativeTo ->
                obj.aaMutations(geneFeature, relativeTo)?.encode(",") ?: "-"
            }
            val detailedMutationsFormat =
                "Format <nt_mutation>:<aa_mutation_individual>:<aa_mutation_cumulative>, where <aa_mutation_individual> is an expected amino acid " +
                        "mutation given no other mutations have occurred, and <aa_mutation_cumulative> amino acid mutation is the observed amino acid " +
                        "mutation combining effect from all other. WARNING: format may change in following versions."
            this += FieldWithParameters(
                Order.`-mutationsDetailed`,
                "-mutationsDetailed",
                "Detailed list of nucleotide and corresponding amino acid mutations. $detailedMutationsFormat",
                geneFeatureParam("Detailed mutations in ", "mutationsDetailedIn"),
                { geneFeature -> validateFeaturesForMutationsExtraction(geneFeature) }
            ) { obj: VDJCObject, geneFeature ->
                obj.mutationsDetailed(geneFeature) ?: "-"
            }
            this += FieldWithParameters(
                Order.`-mutationsDetailedRelative`,
                "-mutationsDetailedRelative",
                "Detailed list of nucleotide and corresponding amino acid mutations written, positions relative to specified gene feature. $detailedMutationsFormat",
                geneFeatureParam("Detailed mutations in ", "mutationsDetailedIn"),
                relativeGeneFeatureParam(),
                { geneFeature, relativeTo -> validateFeaturesForMutationsExtraction(geneFeature, relativeTo) }
            ) { obj: VDJCObject, geneFeature, relativeTo ->
                obj.mutationsDetailed(geneFeature, relativeTo) ?: "-"
            }
        }
        this += FieldWithParameters(
            Order.positions + 100,
            "-positionInReferenceOf",
            "Export position of specified reference point inside reference sequences (clonal sequence / read sequence).",
            FieldWithParameters.CommandArg(
                "<reference_point>",
                { _, arg -> ReferencePoint.parse(arg) },
                { "Position in reference of " },
                { "positionInReferenceOf" }
            )
        ) { obj: VDJCObject, refPoint ->
            obj.positionOfReferencePoint(refPoint, true)
        }
        this += FieldWithParameters(
            Order.positions + 200,
            "-positionOf",
            "Export position of specified reference point inside target sequences (clonal sequence / read sequence).",
            FieldWithParameters.CommandArg(
                "<reference_point>",
                { _, arg -> ReferencePoint.parse(arg) },
                { "Position of " },
                { "positionOf" }
            )
        ) { obj: VDJCObject, refPoint ->
            obj.positionOfReferencePoint(refPoint, false)
        }
        this += FieldParameterless(
            Order.positions + 300,
            "-defaultAnchorPoints",
            "Outputs a list of default reference points (like CDR2Begin, FR4End, etc. " +
                    "see documentation for the full list and formatting)",
            "Ref. points",
            "refPoints"
        ) { obj: VDJCObject ->
            obj.extractRefPoints()
        }
        this += FieldParameterless(
            Order.targets + 100,
            "-targetSequences",
            "Export aligned sequences (targets), separated with comma",
            "Target sequences", "targetSequences"
        ) { obj: VDJCObject ->
            val sb = StringBuilder()
            var i = 0
            while (true) {
                sb.append(obj.getTarget(i).sequence)
                if (i == obj.numberOfTargets() - 1) break
                sb.append(",")
                i++
            }
            sb.toString()
        }
        this += FieldParameterless(
            Order.targets + 200,
            "-targetQualities",
            "Export aligned sequence (target) qualities, separated with comma",
            "Target sequence qualities", "targetQualities"
        ) { obj: VDJCObject ->
            val sb = StringBuilder()
            var i = 0
            while (true) {
                sb.append(obj.getTarget(i).quality)
                if (i == obj.numberOfTargets() - 1) break
                sb.append(",")
                i++
            }
            sb.toString()
        }
        GeneType.values().forEachIndexed { index, type ->
            val c = type.letter.lowercaseChar().toString() + "IdentityPercents"
            this += FieldParameterless(
                Order.identityPercents + 100 + index,
                "-$c",
                type.letter.toString() + " alignment identity percents",
                type.letter.toString() + " alignment identity percents",
                c
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@FieldParameterless NULL
                val sb = StringBuilder()
                var i = 0
                while (true) {
                    sb.append(hits[i].identity)
                    if (i == hits.size - 1) break
                    sb.append(",")
                    i++
                }
                sb.toString()
            }
        }
        GeneType.values().forEachIndexed { index, type ->
            val c = type.letter.lowercaseChar().toString() + "BestIdentityPercent"
            this += FieldParameterless(
                Order.identityPercents + 200 + index,
                "-$c",
                type.letter.toString() + " best alignment identity percent",
                type.letter.toString() + "best alignment identity percent",
                c
            ) { vdjcObject: VDJCObject ->
                val hit = vdjcObject.getBestHit(type) ?: return@FieldParameterless NULL
                hit.identity.toString()
            }
        }
        this += FieldParameterless(
            Order.labels + 100,
            "-chains",
            "Chains",
            "Chains",
            "chains"
        ) { vdjcObject: VDJCObject ->
            vdjcObject.commonChains().toString()
        }
        this += FieldParameterless(
            Order.labels + 200,
            "-topChains",
            "Top chains",
            "Top chains",
            "topChains"
        ) { vdjcObject: VDJCObject ->
            vdjcObject.commonTopChains().toString()
        }
        this += FieldWithParameters(
            Order.labels + 300,
            "-geneLabel",
            "Export gene label (i.e. ReliableChain)",
            FieldWithParameters.CommandArg(
                "<label>",
                { _, geneLabel -> geneLabel },
                { geneLabel -> "Gene Label $geneLabel" },
                { geneLabel -> "geneLabel$geneLabel" }
            )
        ) { vdjcObject: VDJCObject, geneLabel ->
            vdjcObject.getGeneLabel(geneLabel)
        }
        this += FieldParameterless(
            Order.tags + 100,
            "-tagCounts",
            "All tags with counts",
            "All tags counts",
            "tagCounts"
        ) { vdjcObject: VDJCObject ->
            vdjcObject.tagCount.toString()
        }
        this += FieldWithParameters(
            Order.tags + 300,
            "-tag",
            "Tag value (i.e. CELL barcode or UMI sequence)",
            tagParameter("Tag Value ", "tagValue"),
            validateArgs = { (tagName, idx) ->
                require(idx != -1) { "No tag with name $tagName" }
            }
        ) { vdjcObject: VDJCObject, (_, idx) ->
            val tagValue = vdjcObject.tagCount.singleOrNull(idx) ?: return@FieldWithParameters NULL
            tagValue.toString()
        }
        this += FieldWithParameters(
            Order.tags + 400,
            "-uniqueTagCount",
            "Unique tag count",
            tagParameter("Unique ", "unique", hSuffix = " count", sSuffix = "Count"),
            validateArgs = { (tagName, idx) ->
                require(idx != -1) { "No tag with name $tagName" }
            }
        ) { vdjcObject: VDJCObject, (_, idx) ->
            val level = idx + 1
            vdjcObject.getTagDiversity(level).toString()
        }
    }
}

private class Holder(val str: String?, val score: Float) : Comparable<Holder> {
    override fun compareTo(other: Holder): Int = other.score.compareTo(score)
}

private fun VDJCObject.hitsDescription(geneType: GeneType, description: (VDJCHit) -> String): String {
    val familyScores = TObjectFloatHashMap<String>()
    val hits = getHits(geneType)
    if (hits.isEmpty()) return ""
    for (hit in hits) {
        val s = description(hit)
        if (!familyScores.containsKey(s)) familyScores.put(s, hit.score)
    }
    val hs = arrayOfNulls<Holder>(familyScores.size())
    val it = familyScores.iterator()
    var i = 0
    while (it.hasNext()) {
        it.advance()
        hs[i++] = Holder(it.key(), it.value())
    }
    Arrays.sort(hs)
    val sb = StringBuilder()
    i = 0
    while (true) {
        sb.append(hs[i]!!.str)
        if (i == hs.size - 1) break
        sb.append(",")
        i++
    }
    return sb.toString()
}

private fun AbstractField<VDJCObject>.validateFeaturesForMutationsExtraction(
    geneFeature: GeneFeature,
    relativeTo: GeneFeature = geneFeature
) {
    require(relativeTo.contains(geneFeature)) {
        "$cmdArgName: Base feature ${GeneFeature.encode(relativeTo)} " +
                "does not contain relative feature ${GeneFeature.encode(geneFeature)}"
    }

    for (feature in arrayOf(geneFeature, relativeTo)) {
        requireNotNull(feature.geneType) {
            "$cmdArgName: Gene feature ${GeneFeature.encode(feature)} covers several gene types (not possible to select corresponding alignment)"
        }
    }
    require(!relativeTo.isAlignmentAttached) {
        "%$cmdArgName: Alignment attached base gene features not allowed (error in ${GeneFeature.encode(relativeTo)})"
    }
}

private fun VDJCObject.nMutations(
    geneFeature: GeneFeature,
    relativeTo: GeneFeature = geneFeature
): Mutations<NucleotideSequence>? =
    extractMutations(geneFeature, relativeTo) { mutations, _, _, _, _ -> mutations }

private fun VDJCObject.aaMutations(
    geneFeature: GeneFeature,
    relativeTo: GeneFeature = geneFeature
): Mutations<AminoAcidSequence>? =
    extractMutations(geneFeature, relativeTo) { mutations, seq1, _, range, tr ->
        if (tr == null) return@extractMutations null
        val aaMutations = MutationsUtil.nt2aa(seq1, mutations, tr, MAX_SHIFTED_TRIPLETS) ?: return@extractMutations null
        val aaPosFrom =
            AminoAcidSequence.convertNtPositionToAA(range.from, seq1.size(), tr) ?: return@extractMutations null
        val aaPosTo = AminoAcidSequence.convertNtPositionToAA(range.to, seq1.size(), tr) ?: return@extractMutations null
        aaMutations.extractAbsoluteMutationsForRange(aaPosFrom.floor(), aaPosTo.ceil())
    }

private fun VDJCObject.mutationsDetailed(
    geneFeature: GeneFeature,
    relativeTo: GeneFeature = geneFeature
): String? =
    extractMutations(geneFeature, relativeTo) { mutations, seq1, _, _, tr ->
        if (tr == null) return@extractMutations null
        val descriptors =
            MutationsUtil.nt2aaDetailed(seq1, mutations, tr, MAX_SHIFTED_TRIPLETS) ?: return@extractMutations null
        val sb = StringBuilder()
        for (i in descriptors.indices) {
            sb.append(descriptors[i])
            if (i == descriptors.size - 1) break
            sb.append(",")
        }
        sb.toString()
    }

private fun <R : Any> VDJCObject.extractMutations(
    geneFeature: GeneFeature,
    relativeTo: GeneFeature,
    convert: (mutations: Mutations<NucleotideSequence>, seq1: NucleotideSequence, seq2: NucleotideSequence?, range: Range, tr: TranslationParameters?) -> R?
): R? {
    val geneType = geneFeature.geneType!!
    val hit = getBestHit(geneType) ?: return null
    val alignedFeature = hit.alignedFeature
    // if (!alignedFeature.contains(smallGeneFeature))
    //     return "-";
    val gene = hit.gene
    val germlinePartitioning = gene.partitioning
    if (!germlinePartitioning.isAvailable(relativeTo)) return null
    val bigTargetRange = germlinePartitioning.getRelativeRange(alignedFeature, relativeTo)
    var smallTargetRange = when {
        geneFeature.isAlignmentAttached -> null
        else -> germlinePartitioning.getRelativeRange(alignedFeature, geneFeature)
    }
    if (smallTargetRange == null) {
        for (i in 0 until numberOfTargets()) {
            val pt: SequencePartitioning = getPartitionedTarget(i).partitioning
            val range = pt.getRange(geneFeature) ?: continue
            val alignment = getBestHit(geneType).getAlignment(i)
            smallTargetRange = alignment.convertToSeq1Range(range)
            if (smallTargetRange != null) break
        }
    }
    if (smallTargetRange == null) return null
    val intersectionBigAligned = GeneFeature.intersectionStrict(relativeTo, alignedFeature)
    for (i in 0 until hit.numberOfTargets()) {
        val alignment = hit.getAlignment(i)
        if (alignment == null || !alignment.sequence1Range.contains(smallTargetRange)) continue
        val mutations = if (geneFeature == relativeTo) {
            alignment.absoluteMutations.extractRelativeMutationsForRange(smallTargetRange)
        } else {
            val mutations = alignment.absoluteMutations.extractAbsoluteMutationsForRange(smallTargetRange)
            val baIntersectionBegin = intersectionBigAligned.firstPoint
            val referencePosition =
                germlinePartitioning.getRelativePosition(alignedFeature, baIntersectionBegin)
            val bigFeaturePosition =
                germlinePartitioning.getRelativePosition(relativeTo, baIntersectionBegin)
            if (bigFeaturePosition < 0 || referencePosition < 0) continue
            val shift = bigFeaturePosition - referencePosition
            when {
                shift >= 0 -> mutations.move(shift)
                else -> mutations.getRange(
                    Mutations.pabs(mutations.firstMutationWithPosition(-shift)),
                    mutations.size()
                )
            }
        }
        return convert(
            mutations,
            gene.getFeature(relativeTo),
            getFeature(geneFeature).sequence,
            bigTargetRange.getRelativeRangeOf(smallTargetRange),
            germlinePartitioning.getTranslationParameters(relativeTo)
        )
    }
    return null
}

private fun VDJCObject.extractRefPoints(): String {
    val sb = StringBuilder()
    val bestVHit = getBestHit(GeneType.Variable)
    val bestDHit = getBestHit(GeneType.Diversity)
    val bestJHit = getBestHit(GeneType.Joining)
    var i = 0
    while (true) {
        val partitioning: SequencePartitioning = getPartitionedTarget(i).partitioning
        var j = 0
        while (true) {
            val refPoint = ReferencePoint.DefaultReferencePoints[j]

            // Processing special cases for number of deleted / P-segment nucleotides
            if (refPoint == ReferencePoint.VEnd) sb.append(
                trimmedPosition(
                    bestVHit,
                    i,
                    ReferencePoint.VEndTrimmed,
                    ReferencePoint.VEnd
                )
            ) else if (refPoint == ReferencePoint.DBegin) sb.append(
                trimmedPosition(bestDHit, i, ReferencePoint.DBeginTrimmed, ReferencePoint.DBegin)
            ) else if (refPoint == ReferencePoint.DEnd) sb.append(
                trimmedPosition(
                    bestDHit,
                    i,
                    ReferencePoint.DEndTrimmed,
                    ReferencePoint.DEnd
                )
            ) else if (refPoint == ReferencePoint.JBegin) sb.append(
                trimmedPosition(bestJHit, i, ReferencePoint.JBeginTrimmed, ReferencePoint.JBegin)
            ) else {
                // Normal points
                val referencePointPosition = partitioning.getPosition(refPoint)
                if (referencePointPosition >= 0) sb.append(referencePointPosition)
            }
            if (j == ReferencePoint.DefaultReferencePoints.size - 1) break
            sb.append(":")
            j++
        }
        if (i == numberOfTargets() - 1) break
        sb.append(",")
        i++
    }
    return sb.toString()
}

private fun trimmedPosition(
    hit: VDJCHit?,
    targetId: Int,
    trimmedPoint: ReferencePoint,
    boundaryPoint: ReferencePoint
): String {
    require(trimmedPoint.isAttachedToAlignmentBound)

    // No hit - no point
    if (hit == null) return ""
    val alignment = hit.getAlignment(targetId) ?: return ""

    // If alignment is not defined for this target
    val ap = trimmedPoint.activationPoint
    val seq1Range = alignment.sequence1Range
    if (ap != null) {
        // Point is valid only if activation point is reached
        val apPositionInSeq1 = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, ap)
        if (if (apPositionInSeq1 < 0 ||
                trimmedPoint.isAttachedToLeftAlignmentBound
            ) seq1Range.from > apPositionInSeq1 else seq1Range.to <= apPositionInSeq1
        ) return ""
    }
    val bpPositionInSeq1 = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, boundaryPoint)

    // Just in case
    return when {
        bpPositionInSeq1 < 0 -> ""
        else -> when {
            trimmedPoint.isAttachedToLeftAlignmentBound -> bpPositionInSeq1 - seq1Range.from
            else -> seq1Range.to - bpPositionInSeq1
        }.toString()
    }
}

private fun VDJCObject.positionOfReferencePoint(
    referencePoint: ReferencePoint,
    inReference: Boolean
): String {
    val sb = StringBuilder()
    var i = 0
    while (true) {
        var positionInTarget = getPartitionedTarget(i).partitioning.getPosition(referencePoint)
        if (inReference) {
            val hit = getBestHit(referencePoint.geneType)
            if (hit != null) {
                val al = hit.getAlignment(i)
                if (al != null) positionInTarget = al.convertToSeq1Position(positionInTarget)
            }
        }
        sb.append(positionInTarget)
        if (i == numberOfTargets() - 1) break
        sb.append(",")
        i++
    }
    return sb.toString()
}

internal fun stdDeprecationNote(oldName: String, newName: String, newHeader: Boolean) =
    "$oldName field is deprecated, please use $newName.${NEW_HEADER_NOTE.takeIf { newHeader } ?: ""}"

internal const val NEW_HEADER_NOTE =
    " Please also note that the column header name will be different with the new option."

internal fun tagParameter(
    hPrefix: String,
    sPrefix: String,
    hSuffix: String = "",
    sSuffix: String = ""
) = FieldWithParameters.CommandArg(
    "<tag_name>",
    { header, tagName -> tagName to header.tagsInfo.indexOf(tagName) },
    { (tagName, _) -> hPrefix + tagName + hSuffix },
    { (tagName, _) -> sPrefix + tagName + sSuffix }
)

private fun geneFeatureParam(
    hPrefix: String,
    sPrefix: String
): FieldWithParameters.CommandArg<GeneFeature> = FieldWithParameters.CommandArg(
    "<gene_feature>",
    { _, arg -> GeneFeature.parse(arg) },
    { hPrefix + GeneFeature.encode(it) },
    { sPrefix + GeneFeature.encode(it) }
)

private fun relativeGeneFeatureParam(): FieldWithParameters.CommandArg<GeneFeature> = FieldWithParameters.CommandArg(
    "<relative_to_gene_feature>",
    { _, arg -> GeneFeature.parse(arg) },
    { "relative to " + GeneFeature.encode(it) },
    { "Relative" + GeneFeature.encode(it) }
)
