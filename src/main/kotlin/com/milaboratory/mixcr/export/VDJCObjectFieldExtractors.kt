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
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.export.FieldExtractorsFactory.Order
import com.milaboratory.mixcr.export.GeneFeaturesRangeUtil.commonDescriptionForFeatures
import com.milaboratory.mixcr.export.GeneFeaturesRangeUtil.commonDescriptionForReferencePoints
import com.milaboratory.mixcr.export.GeneFeaturesRangeUtil.geneFeaturesBetween
import com.milaboratory.mixcr.export.GeneFeaturesRangeUtil.referencePointsToExport
import com.milaboratory.mixcr.export.ParametersFactory.geneFeatureParam
import com.milaboratory.mixcr.export.ParametersFactory.referencePointParam
import com.milaboratory.mixcr.export.ParametersFactory.referencePointParamOptional
import com.milaboratory.mixcr.export.ParametersFactory.relativeGeneFeatureParam
import com.milaboratory.mixcr.export.ParametersFactory.tagParam
import com.milaboratory.mixcr.export.ParametersFactory.tagTypeDescription
import com.milaboratory.mixcr.export.ParametersFactory.tagTypeParam
import gnu.trove.map.hash.TObjectFloatHashMap
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.ReferencePoint.DBegin
import io.repseq.core.ReferencePoint.DBeginTrimmed
import io.repseq.core.ReferencePoint.DEnd
import io.repseq.core.ReferencePoint.DEndTrimmed
import io.repseq.core.ReferencePoint.DefaultReferencePoints
import io.repseq.core.ReferencePoint.JBegin
import io.repseq.core.ReferencePoint.JBeginTrimmed
import io.repseq.core.ReferencePoint.VEnd
import io.repseq.core.ReferencePoint.VEndTrimmed
import io.repseq.core.SequencePartitioning
import java.text.DecimalFormat
import java.util.*

private val SCORE_FORMAT = DecimalFormat("#.#")
private const val MAX_SHIFTED_TRIPLETS = 3

object VDJCObjectFieldExtractors {
    fun vdjcObjectFields(forTreesExport: Boolean): List<FieldsCollection<VDJCObject>> = buildList {
        // Number of targets
        this += Field(
            Order.targetsCount + 100,
            "-targets",
            "Export number of targets",
            "numberOfTargets"
        ) { vdjcObject: VDJCObject -> vdjcObject.numberOfTargets().toString() }

        // Best hits
        GeneType.values().forEach { type ->
            if (!forTreesExport || type !in GeneType.VJ_REFERENCE) {
                val l = type.letter
                this += Field(
                    Order.orderForBestHit(type),
                    "-${l.lowercaseChar()}Hit",
                    "Export best $l hit",
                    "best${l}Hit"
                ) { vdjcObject: VDJCObject ->
                    val bestHit = vdjcObject.getBestHit(type) ?: return@Field NULL
                    bestHit.gene.name
                }
            }
        }

        // Best gene
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += Field(
                Order.hits + 200 + index,
                "-${l.lowercaseChar()}Gene",
                "Export best $l hit gene name (e.g. TRBV12-3 for TRBV12-3*00)",
                "best${l}Gene"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@Field NULL
                bestHit.gene.geneName
            }
        }

        // Best family
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += Field(
                Order.hits + 300 + index,
                "-${l.lowercaseChar()}Family",
                "Export best $l hit family name (e.g. TRBV12 for TRBV12-3*00)",
                "best${l}Family"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@Field NULL
                bestHit.gene.familyName
            }
        }

        // Best hit score
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += Field(
                Order.hits + 400 + index,
                "-${l.lowercaseChar()}HitScore",
                "Export score for best $l hit",
                "best${l}HitScore"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@Field NULL
                bestHit.score.toString()
            }
        }

        // All hits
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += Field(
                Order.hits + 500 + index,
                "-${l.lowercaseChar()}HitsWithScore",
                "Export all $l hits with score",
                "all${l}HitsWithScore"
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@Field NULL
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
            this += Field(
                Order.hits + 600 + index,
                "-${l.lowercaseChar()}Hits",
                "Export all $l hits",
                "all${l}Hits"
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@Field NULL
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
            this += Field(
                Order.hits + 700 + index,
                "-${l.lowercaseChar()}Genes",
                "Export all $l gene names (e.g. TRBV12-3 for TRBV12-3*00)",
                "all${l}Genes"
            ) { vdjcObject: VDJCObject ->
                vdjcObject.hitsDescription(type) { it.gene.geneName }
            }
        }

        // All families
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += Field(
                Order.hits + 800 + index,
                "-${l.lowercaseChar()}Families",
                "Export all $l gene family anmes (e.g. TRBV12 for TRBV12-3*00)",
                "all${l}Families",
            ) { vdjcObject: VDJCObject ->
                vdjcObject.hitsDescription(type) { it.gene.familyName }
            }
        }

        // Best alignment
        GeneType.values().forEachIndexed { index, type ->
            val l = type.letter
            this += Field(
                Order.alignments + 100 + index,
                "-${l.lowercaseChar()}Alignment",
                "Export best $l alignment",
                "best${l}Alignment"
            ) { vdjcObject: VDJCObject ->
                val bestHit = vdjcObject.getBestHit(type) ?: return@Field NULL
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
            this += Field(
                Order.alignments + 200 + index,
                "-${l.lowercaseChar()}Alignments",
                "Export all $l alignments",
                "all${l}Alignments"
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@Field NULL
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
        val nFeatureField = Field(
            Order.`-nFeature`,
            "-nFeature",
            "Export nucleotide sequence of specified gene feature",
            geneFeatureParam("nSeq")
        ) { vdjcObject: VDJCObject, feature ->
            vdjcObject.getFeature(feature)?.sequence?.toString() ?: NULL
        }
        if (!forTreesExport) {
            this += nFeatureField
            this += FieldsCollection(
                Order.`-nFeature` + 1,
                "-allNFeatures",
                "Export nucleotide sequences ${commonDescriptionForFeatures("-allNFeatures", nFeatureField)}",
                nFeatureField,
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }
        }
        val qFeatureField = Field(
            Order.features + 200,
            "-qFeature",
            "Export quality string of specified gene feature",
            geneFeatureParam("qual")
        ) { vdjcObject: VDJCObject, feature ->
            vdjcObject.getFeature(feature)?.quality?.toString() ?: NULL
        }
        this += qFeatureField
        this += FieldsCollection(
            Order.features + 201,
            "-allQFeatures",
            "Export quality string ${commonDescriptionForFeatures("-allQFeatures", qFeatureField)}",
            qFeatureField,
            referencePointParamOptional("<from_reference_point>"),
            referencePointParamOptional("<to_reference_point>"),
        ) { from, to ->
            geneFeaturesBetween(from, to)
        }

        if (!forTreesExport) {
            val aaFeatureField = Field(
                Order.`-aaFeature`,
                "-aaFeature",
                "Export amino acid sequence of specified gene feature",
                geneFeatureParam("aaSeq")
            ) { vdjcObject: VDJCObject, geneFeature ->
                val feature = vdjcObject.getFeature(geneFeature) ?: return@Field NULL
                val targetId = vdjcObject.getTargetContainingFeature(geneFeature)
                val tr = if (targetId == -1) {
                    TranslationParameters.FromLeftWithIncompleteCodon
                } else {
                    vdjcObject.getPartitionedTarget(targetId).partitioning.getTranslationParameters(geneFeature)
                }
                if (tr == null) return@Field NULL
                AminoAcidSequence.translate(feature.sequence, tr).toString()
            }
            this += aaFeatureField

            this += FieldsCollection(
                Order.`-aaFeature` + 1,
                "-allAaFeatures",
                "Export amino acid sequence ${commonDescriptionForFeatures("-allAaFeatures", aaFeatureField)}",
                aaFeatureField,
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }
        }
        val nFeatureImputedField = Field(
            Order.features + 400,
            "-nFeatureImputed",
            "Export nucleotide sequence of specified gene feature using letters from germline (marked lowercase) for uncovered regions",
            geneFeatureParam("nSeqImputed")
        ) { vdjcObject: VDJCObject, geneFeature ->
            vdjcObject.getIncompleteFeature(geneFeature)?.toString() ?: NULL
        }
        this += nFeatureImputedField
        this += FieldsCollection(
            Order.features + 401,
            "-allNFeaturesImputed",
            "Export nucleotide sequence using letters from germline (marked lowercase) for uncovered regions " +
                    commonDescriptionForFeatures("-allNFeaturesImputed", nFeatureImputedField),
            nFeatureImputedField,
            referencePointParamOptional("<from_reference_point>"),
            referencePointParamOptional("<to_reference_point>"),
        ) { from, to ->
            geneFeaturesBetween(from, to)
        }

        val aaFeatureImputedField = Field(
            Order.features + 500,
            "-aaFeatureImputed",
            "Export amino acid sequence of specified gene feature using letters from germline (marked lowercase) for uncovered regions",
            geneFeatureParam("aaSeqImputed")
        ) { vdjcObject: VDJCObject, geneFeature ->
            vdjcObject.getIncompleteFeature(geneFeature)?.toAminoAcidString() ?: NULL
        }
        this += aaFeatureImputedField
        this += FieldsCollection(
            Order.features + 501,
            "-allAaFeaturesImputed",
            "Export amino acid sequence using letters from germline (marked lowercase) for uncovered regions " +
                    commonDescriptionForFeatures("-allAaFeaturesImputed", aaFeatureImputedField),
            aaFeatureImputedField,
            referencePointParamOptional("<from_reference_point>"),
            referencePointParamOptional("<to_reference_point>"),
        ) { from, to ->
            geneFeaturesBetween(from, to)
        }

        val minFeatureQualityField = Field(
            Order.features + 600,
            "-minFeatureQuality",
            "Export minimal quality of specified gene feature",
            geneFeatureParam("minQual")
        ) { vdjcObject: VDJCObject, feature ->
            vdjcObject.getFeature(feature)?.quality?.minValue()?.toString() ?: NULL
        }
        this += minFeatureQualityField
        this += FieldsCollection(
            Order.features + 601,
            "-allMinFeaturesQuality",
            "Export minimal quality ${commonDescriptionForFeatures("-allMinFeaturesQuality", minFeatureQualityField)}",
            minFeatureQualityField,
            referencePointParamOptional("<from_reference_point>"),
            referencePointParamOptional("<to_reference_point>"),
        ) { from, to ->
            geneFeaturesBetween(from, to)
        }

        if (!forTreesExport) {
            this += FieldsCollection(
                Order.features + 610,
                "-allNFeaturesWithMinQuality",
                "Export nucleotide sequences and minimal quality ${
                    commonDescriptionForFeatures(
                        "-allNFeaturesWithMinQuality",
                        nFeatureField,
                        minFeatureQualityField
                    )
                }",
                listOf(nFeatureField, minFeatureQualityField),
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }
            this += FieldsCollection(
                Order.features + 611,
                "-allNFeaturesImputedWithMinQuality",
                "Export nucleotide sequences and minimal quality ${
                    commonDescriptionForFeatures(
                        "-allNFeaturesImputedWithMinQuality",
                        nFeatureImputedField,
                        minFeatureQualityField
                    )
                }",
                listOf(nFeatureImputedField, minFeatureQualityField),
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }
        }

        val avrgFeatureQualityField = Field(
            Order.features + 700,
            "-avrgFeatureQuality",
            "Export average quality of specified gene feature",
            geneFeatureParam("meanQual")
        ) { vdjcObject: VDJCObject, feature ->
            vdjcObject.getFeature(feature)?.quality?.meanValue()?.toString() ?: NULL
        }
        this += avrgFeatureQualityField
        this += FieldsCollection(
            Order.features + 701,
            "-allAvrgFeaturesQuality",
            "Export average quality " +
                    commonDescriptionForFeatures("-allAvrgFeaturesQuality", avrgFeatureQualityField),
            avrgFeatureQualityField,
            referencePointParamOptional("<from_reference_point>"),
            referencePointParamOptional("<to_reference_point>"),
        ) { from, to ->
            geneFeaturesBetween(from, to)
        }

        if (!forTreesExport) {
            val lengthOf = Field(
                Order.`-lengthOf`,
                "-lengthOf",
                "Export length of specified gene feature.",
                geneFeatureParam("lengthOf")
            ) { vdjcObject: VDJCObject, feature ->
                vdjcObject.getFeature(feature)?.size()?.toString() ?: NULL
            }
            this += lengthOf
            this += FieldsCollection(
                Order.`-lengthOf` + 1,
                "-allLengthOf",
                "Export length ${commonDescriptionForFeatures("-allLengthOf", lengthOf)}",
                lengthOf,
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }

            val nMutationsField = Field(
                Order.`-nMutations`,
                "-nMutations",
                "Extract nucleotide mutations for specific gene feature; relative to germline sequence.",
                geneFeatureParam("nMutations"),
                { geneFeature -> validateFeaturesForMutationsExtraction(geneFeature) }
            ) { obj: VDJCObject, geneFeature ->
                obj.nMutations(geneFeature)?.encode(",") ?: "-"
            }
            this += nMutationsField
            this += FieldsCollection(
                Order.`-nMutations` + 1,
                "-allNMutations",
                "Extract nucleotide mutations relative to germline sequence " +
                        commonDescriptionForFeatures("-allNMutations", nMutationsField),
                nMutationsField,
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }

            this += Field(
                Order.`-nMutationsRelative`,
                "-nMutationsRelative",
                "Extract nucleotide mutations for specific gene feature relative to another feature.",
                geneFeatureParam("nMutationsIn"),
                relativeGeneFeatureParam(),
                { geneFeature, relativeTo -> validateFeaturesForMutationsExtraction(geneFeature, relativeTo) }
            ) { obj: VDJCObject, geneFeature, relativeTo ->
                obj.nMutations(geneFeature, relativeTo)?.encode(",") ?: "-"
            }
            val aaMutationsFiled = Field(
                Order.`-aaMutations`,
                "-aaMutations",
                "Extract amino acid mutations for specific gene feature",
                geneFeatureParam("aaMutations"),
                { geneFeature -> validateFeaturesForMutationsExtraction(geneFeature) }
            ) { obj: VDJCObject, geneFeature ->
                obj.aaMutations(geneFeature)?.encode(",") ?: "-"
            }
            this += aaMutationsFiled
            this += FieldsCollection(
                Order.`-aaMutations` + 1,
                "-allAaMutations",
                "Extract amino acid nucleotide mutations relative to germline sequence " +
                        commonDescriptionForFeatures("-allAaMutations", aaMutationsFiled),
                aaMutationsFiled,
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }

            this += Field(
                Order.`-aaMutationsRelative`,
                "-aaMutationsRelative",
                "Extract amino acid mutations for specific gene feature relative to another feature.",
                geneFeatureParam("aaMutationsIn"),
                relativeGeneFeatureParam(),
                { geneFeature, relativeTo -> validateFeaturesForMutationsExtraction(geneFeature, relativeTo) }
            ) { obj: VDJCObject, geneFeature, relativeTo ->
                obj.aaMutations(geneFeature, relativeTo)?.encode(",") ?: "-"
            }
            val detailedMutationsFormat =
                "Format <nt_mutation>:<aa_mutation_individual>:<aa_mutation_cumulative>, where <aa_mutation_individual> is an expected amino acid " +
                        "mutation given no other mutations have occurred, and <aa_mutation_cumulative> amino acid mutation is the observed amino acid " +
                        "mutation combining effect from all other. WARNING: format may change in following versions."
            val mutationsDetailedField = Field(
                Order.`-mutationsDetailed`,
                "-mutationsDetailed",
                "Detailed list of nucleotide and corresponding amino acid mutations. $detailedMutationsFormat",
                geneFeatureParam("mutationsDetailedIn"),
                { geneFeature -> validateFeaturesForMutationsExtraction(geneFeature) }
            ) { obj: VDJCObject, geneFeature ->
                obj.mutationsDetailed(geneFeature) ?: "-"
            }
            this += mutationsDetailedField
            this += FieldsCollection(
                Order.`-mutationsDetailed` + 1,
                "-allMutationsDetailed",
                "Detailed list of nucleotide and corresponding amino acid mutations " +
                        commonDescriptionForFeatures("-allMutationsDetailed", mutationsDetailedField),
                mutationsDetailedField,
                referencePointParamOptional("<from_reference_point>"),
                referencePointParamOptional("<to_reference_point>"),
            ) { from, to ->
                geneFeaturesBetween(from, to)
            }

            this += Field(
                Order.`-mutationsDetailedRelative`,
                "-mutationsDetailedRelative",
                "Detailed list of nucleotide and corresponding amino acid mutations written, positions relative to specified gene feature. $detailedMutationsFormat",
                geneFeatureParam("mutationsDetailedIn"),
                relativeGeneFeatureParam(),
                { geneFeature, relativeTo -> validateFeaturesForMutationsExtraction(geneFeature, relativeTo) }
            ) { obj: VDJCObject, geneFeature, relativeTo ->
                obj.mutationsDetailed(geneFeature, relativeTo) ?: "-"
            }
        }
        val positionInReferenceOfField = Field(
            Order.positions + 100,
            "-positionInReferenceOf",
            "Export position of specified reference point inside reference sequences (clonal sequence / read sequence).",
            referencePointParam("positionInReferenceOf")
        ) { obj: VDJCObject, refPoint ->
            obj.positionOfReferencePoint(refPoint, true)
        }
        this += positionInReferenceOfField
        this += FieldsCollection(
            Order.positions + 101,
            "-allPositionsInReferenceOf",
            "Export position inside reference sequences (clonal sequence / read sequence) ${
                commonDescriptionForReferencePoints(
                    "-allPositionsInReferenceOf",
                    positionInReferenceOfField
                )
            }",
            positionInReferenceOfField,
            referencePointParamOptional("<from_reference_point>"),
            referencePointParamOptional("<to_reference_point>"),
        ) { from, to ->
            referencePointsToExport(from, to)
        }

        val positionOfField = Field(
            Order.positions + 200,
            "-positionOf",
            "Export position of specified reference point inside target sequences (clonal sequence / read sequence).",
            referencePointParam("positionOf")
        ) { obj: VDJCObject, refPoint ->
            obj.positionOfReferencePoint(refPoint, false)
        }
        this += positionOfField
        this += FieldsCollection(
            Order.positions + 201,
            "-allPositionsOf",
            "Export position inside target sequences (clonal sequence / read sequence) ${
                commonDescriptionForReferencePoints(
                    "-allPositionsOf",
                    positionOfField
                )
            }",
            positionOfField,
            referencePointParamOptional("<from_reference_point>"),
            referencePointParamOptional("<to_reference_point>"),
        ) { from, to ->
            referencePointsToExport(from, to)
        }

        this += Field(
            Order.positions + 300,
            "-defaultAnchorPoints",
            "Outputs a list of default reference points (like CDR2Begin, FR4End, etc. " +
                    "see documentation for the full list and formatting)",
            "refPoints"
        ) { obj: VDJCObject ->
            obj.extractRefPoints()
        }
        this += Field(
            Order.targets + 100,
            "-targetSequences",
            "Export aligned sequences (targets), separated with comma",
            "targetSequences"
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
        this += Field(
            Order.targets + 200,
            "-targetQualities",
            "Export aligned sequence (target) qualities, separated with comma",
            "targetQualities"
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
            this += Field(
                Order.identityPercents + 100 + index,
                "-$c",
                type.letter.toString() + " alignment identity percents",
                c
            ) { vdjcObject: VDJCObject ->
                val hits = vdjcObject.getHits(type)
                if (hits.isEmpty()) return@Field NULL
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
            this += Field(
                Order.identityPercents + 200 + index,
                "-$c",
                type.letter.toString() + " best alignment identity percent",
                c
            ) { vdjcObject: VDJCObject ->
                val hit = vdjcObject.getBestHit(type) ?: return@Field NULL
                hit.identity.toString()
            }
        }
        this += Field(
            Order.labels + 100,
            "-chains",
            "Chains",
            "chains"
        ) { vdjcObject: VDJCObject ->
            vdjcObject.commonChains().toString()
        }
        this += Field(
            Order.labels + 200,
            "-topChains",
            "Top chains",
            "topChains"
        ) { vdjcObject: VDJCObject ->
            vdjcObject.commonTopChains().toString()
        }
        this += Field(
            Order.labels + 300,
            "-geneLabel",
            "Export gene label (i.e. ReliableChain)",
            CommandArgRequired(
                "<label>",
                { _, geneLabel -> geneLabel }
            ) { geneLabel -> "geneLabel$geneLabel" }
        ) { vdjcObject: VDJCObject, geneLabel ->
            vdjcObject.getGeneLabel(geneLabel)
        }
        this += Field(
            Order.tags + 100,
            "-tagCounts",
            "All tags with counts",
            "tagCounts"
        ) { vdjcObject: VDJCObject ->
            vdjcObject.tagCount.toString()
        }
        val tagField = Field(
            Order.tags + 300,
            "-tag",
            "Tag value (i.e. CELL barcode or UMI sequence)",
            tagParam("tagValue")
        ) { vdjcObject: VDJCObject, tag: TagInfo ->
            val tagValue = vdjcObject.tagCount.singleOrNull(tag.index) ?: return@Field NULL
            tagValue.toString()
        }
        this += tagField
        this += FieldsCollection.invoke(
            Order.tags + 301,
            "-allTags",
            "Tag values (i.e. CELL barcode or UMI sequence) for all available tags in separate columns.%n$tagTypeDescription",
            tagField,
            tagTypeParam()
        ) { tagType ->
            tagsInfo.filter { it.type == tagType }.map { arrayOf(it.name) }
        }

        val uniqueTagCountField = Field(
            Order.tags + 400,
            "-uniqueTagCount",
            "Unique tag count",
            tagParam("unique", sSuffix = "Count")
        ) { vdjcObject: VDJCObject, tag: TagInfo ->
            val level = tag.index + 1
            vdjcObject.getTagDiversity(level).toString()
        }
        this += uniqueTagCountField
        this += FieldsCollection(
            Order.tags + 401,
            "-allUniqueTagsCount",
            "Unique tag count for all available tags in separate columns.%n$tagTypeDescription",
            uniqueTagCountField,
            tagTypeParam()
        ) { tagType ->
            tagsInfo.filter { it.type == tagType }.map { arrayOf(it.name) }
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

private fun Field<VDJCObject>.validateFeaturesForMutationsExtraction(
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
            val refPoint = DefaultReferencePoints[j]

            // Processing special cases for number of deleted / P-segment nucleotides
            if (refPoint == VEnd) sb.append(
                trimmedPosition(
                    bestVHit,
                    i,
                    VEndTrimmed,
                    VEnd
                )
            ) else if (refPoint == DBegin) sb.append(
                trimmedPosition(bestDHit, i, DBeginTrimmed, DBegin)
            ) else if (refPoint == DEnd) sb.append(
                trimmedPosition(
                    bestDHit,
                    i,
                    DEndTrimmed,
                    DEnd
                )
            ) else if (refPoint == JBegin) sb.append(
                trimmedPosition(bestJHit, i, JBeginTrimmed, JBegin)
            ) else {
                // Normal points
                val referencePointPosition = partitioning.getPosition(refPoint)
                if (referencePointPosition >= 0) sb.append(referencePointPosition)
            }
            if (j == DefaultReferencePoints.size - 1) break
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
