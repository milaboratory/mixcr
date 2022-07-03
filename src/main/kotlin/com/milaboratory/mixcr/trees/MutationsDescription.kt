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
@file:Suppress("LocalVariableName", "FunctionName", "PrivatePropertyName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Sequence
import com.milaboratory.core.sequence.TranslationParameters
import com.milaboratory.core.sequence.TranslationParameters.FromCenter
import com.milaboratory.core.sequence.TranslationParameters.FromLeftWithoutIncompleteCodon
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.plus
import io.repseq.core.ExtendedReferencePoints
import io.repseq.core.ExtendedReferencePointsBuilder
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.VJJunction
import io.repseq.core.GeneFeature.intersection
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.*
import io.repseq.core.ReferencePoints
import java.util.*

class MutationsDescription private constructor(
    private val VPartsN: Parts<NucleotideSequence>,
    private val baseNDN: NucleotideSequence,
    private val NDNMutations: Mutations<NucleotideSequence>,
    private val JPartsN: Parts<NucleotideSequence>,
    private val maxShiftedTriplets: Int = Int.MAX_VALUE,
    VPartsAA: MutationsDescription.() -> Parts<AminoAcidSequence>,
    JPartsAA: MutationsDescription.() -> Parts<AminoAcidSequence>,
    fullLengthPartsAA: MutationsDescription.() -> Parts<AminoAcidSequence>,
) {

    constructor(
        VParts: SortedMap<GeneFeature, Mutations<NucleotideSequence>>,
        VSequence1: NucleotideSequence,
        VPartitioning: ExtendedReferencePoints,
        baseNDN: NucleotideSequence,
        NDNMutations: Mutations<NucleotideSequence>,
        JParts: SortedMap<GeneFeature, Mutations<NucleotideSequence>>,
        JSequence1: NucleotideSequence,
        JPartitioning: ExtendedReferencePoints,
        maxShiftedTriplets: Int = Int.MAX_VALUE
    ) : this(
        VPartsN = Parts(VPartitioning, VSequence1, VParts),
        baseNDN = baseNDN,
        NDNMutations = NDNMutations,
        JPartsN = Parts(JPartitioning, JSequence1, JParts),
        maxShiftedTriplets = maxShiftedTriplets,
        VPartsAA = { VPartsN.translate() },
        JPartsAA = { JPartsN.translate() },
        fullLengthPartsAA = { fullLengthPartsN.translate() }
    )

    init {
        check(VPartsN.mutations.lastKey().lastPoint == VEndTrimmed)
        check(JPartsN.mutations.firstKey().firstPoint == JBeginTrimmed)
    }

    private val fullLengthPartsN: Parts<NucleotideSequence> by lazy {
        combinePartsForFullLength(VPartsN, VJJunction, baseNDN, NDNMutations, JPartsN)
    }

    private val VPartsAA: Parts<AminoAcidSequence> by lazy {
        VPartsAA()
    }

    private val JPartsAA: Parts<AminoAcidSequence> by lazy {
        JPartsAA()
    }

    private val fullLengthPartsAA: Parts<AminoAcidSequence> by lazy {
        fullLengthPartsAA()
    }

    private fun <S : Sequence<S>> combinePartsForFullLength(
        VParts: Parts<S>,
        geneFeatureInTheMiddle: GeneFeature,
        sequence1InTheMiddle: S,
        mutationsInTheMiddle: Mutations<S>,
        JParts: Parts<S>
    ): Parts<S> {
        val VPartsEnd = VParts.partitioning.getPosition(geneFeatureInTheMiddle.firstPoint)
        val JPartsBegin = JParts.partitioning.getPosition(geneFeatureInTheMiddle.lastPoint)

        val JBeginTrimmedPosition = VPartsEnd + sequence1InTheMiddle.size()
        val partitioningForFullSequence = ExtendedReferencePointsBuilder().apply {
            setPositionsFrom(VParts.partitioning.without(VEnd))
            setPosition(JBeginTrimmed, JBeginTrimmedPosition)
            setPositionsFrom(JParts.partitioning.without(JBegin).move(JBeginTrimmedPosition - JPartsBegin))
        }.build()

        val VGeneFeature = GeneFeature(VParts.mutations.firstKey().firstPoint, geneFeatureInTheMiddle.firstPoint)
        val JGeneFeature = GeneFeature(geneFeatureInTheMiddle.lastPoint, JParts.mutations.lastKey().lastPoint)

        val combinedSequence1 = VParts.sequence1.getRange(0, VPartsEnd) +
                sequence1InTheMiddle +
                JParts.sequence1.getRange(JPartsBegin, JParts.sequence1.size())

        val croppedVMutations = VParts.cropMutations(VGeneFeature)
        val croppedJMutations = JParts.cropMutations(JGeneFeature)
        val mutationsFromCombinedSequence1: TreeMap<GeneFeature, Mutations<S>> = TreeMap()
        croppedVMutations.keys.toList().subList(0, croppedVMutations.size - 1).forEach { key ->
            mutationsFromCombinedSequence1[key] = croppedVMutations[key]!!
        }
        val lastVPartGeneFeature = croppedVMutations.lastKey()
        val firstJPartGeneFeature = croppedJMutations.firstKey()
        val JMutationsShift = JBeginTrimmedPosition - JPartsBegin
        mutationsFromCombinedSequence1[GeneFeature(lastVPartGeneFeature.firstPoint, firstJPartGeneFeature.lastPoint)] =
            croppedVMutations[lastVPartGeneFeature]!!
                .concat(mutationsInTheMiddle.move(partitioningForFullSequence.getPosition(geneFeatureInTheMiddle.firstPoint)))
                .concat(croppedJMutations[firstJPartGeneFeature]!!.move(JMutationsShift))
        croppedJMutations.keys.toList().subList(1, croppedJMutations.size).forEach { key ->
            mutationsFromCombinedSequence1[key] = croppedJMutations[key]!!.move(JMutationsShift)
        }

        return Parts(
            partitioningForFullSequence,
            combinedSequence1,
            mutationsFromCombinedSequence1
        )
    }


    private class Parts<S : Sequence<S>>(
        val partitioning: ExtendedReferencePoints,
        val sequence1: S,
        val mutations: SortedMap<GeneFeature, Mutations<S>>
    ) {
        fun cropMutations(cropBy: GeneFeature): SortedMap<GeneFeature, Mutations<S>> =
            mutations.entries.associateTo(TreeMap()) { (geneFeature, mutations) ->
                val intersection = intersection(geneFeature, cropBy)
                intersection to mutations.extractAbsoluteMutations(partitioning.getRange(intersection), true)
            }

        val combinedMutations: Mutations<S> by lazy {
            var result: Mutations<S> = Mutations.empty(sequence1.alphabet)
            mutations.values.forEach {
                result = result.concat(it)
            }
            result
        }
    }

    private fun Parts<NucleotideSequence>.translate(): Parts<AminoAcidSequence> {
        val translationParametersByFeatures = mutations.keys.associateTo(TreeMap()) { geneFeature ->
            val withVJJunction = when {
                geneFeature.lastPoint == VEndTrimmed -> GeneFeature(geneFeature.firstPoint, VEnd)
                geneFeature.firstPoint == JBeginTrimmed -> GeneFeature(JBegin, geneFeature.lastPoint)
                else -> geneFeature
            }
            withVJJunction to partitioning.getTranslationParameters(withVJJunction)
        }
        val translationParametersForSequence1 = partitioning.getTranslationParameters(
            GeneFeature(
                translationParametersByFeatures.firstKey().firstPoint,
                translationParametersByFeatures.lastKey().lastPoint
            )
        )
        return Parts(
            ExtendedReferencePointsBuilder().apply {
                for (i in 0 until partitioning.pointsCount()) {
                    val referencePoint = partitioning.referencePointFromIndex(i)
                    val position = partitioning.getPosition(referencePoint)
                    if (position != -1) {
                        val translationParameters = translationParametersByFeatures.entries
                            .firstOrNull { referencePoint in it.key }
                            ?.value

                        if (translationParameters != null) {
                            setPosition(
                                referencePoint,
                                AminoAcidSequence.convertNtPositionToAA(
                                    position,
                                    sequence1.size(),
                                    translationParameters
                                ).aminoAcidPosition
                            )
                        }
                    }
                }
            }.build(),
            AminoAcidSequence.translate(
                sequence1,
                translationParametersForSequence1
            ),
            mutations.mapValuesTo(TreeMap()) { (geneFeature, mutations) ->
                //TODO left only partitioning.getTranslationParameters(geneFeature) after fix of io.repseq.core.GeneFeature.getCodingGeneFeature
                val translationParameters = if (geneFeature.lastPoint == VEndTrimmed) {
                    val VTranslationParameters =
                        partitioning.getTranslationParameters(GeneFeature(geneFeature.firstPoint, CDR3Begin))
                    if (VTranslationParameters == FromCenter) {
                        FromLeftWithoutIncompleteCodon
                    } else if (VTranslationParameters.fromLeft == true) {
                        VTranslationParameters
                    } else {
                        error("Can't rebase $VTranslationParameters from left for $geneFeature")
                    }
                } else if (geneFeature.firstPoint == JBeginTrimmed) {
                    translationParametersForSequence1
                } else {
                    partitioning.getTranslationParameters(geneFeature)
                }
                MutationsUtil.nt2aa(
                    sequence1,
                    mutations,
                    translationParameters,
                    maxShiftedTriplets
                )
            }
        )
    }

    fun targetNSequence(geneFeature: GeneFeature): NucleotideSequence? =
        nAlignment(geneFeature)?.let { alignment ->
            alignment.relativeMutations.mutate(alignment.sequence1.getRange(alignment.sequence1Range))
        }

    fun targetAASequence(geneFeature: GeneFeature): AminoAcidSequence? =
        aaAlignment(geneFeature)?.let { alignment ->
            alignment.relativeMutations.mutate(alignment.sequence1.getRange(alignment.sequence1Range))
        }

    fun nAlignment(geneFeature: GeneFeature, relativeTo: GeneFeature = geneFeature): Alignment<NucleotideSequence>? {
        validateArgs(geneFeature, relativeTo)
        return when (geneFeature.geneType) {
            Variable -> VPartsN
            Joining -> JPartsN
            null -> {
                check(geneFeature == relativeTo)
                checkNotNull(intersection(geneFeature, VJJunction))
                fullLengthPartsN
            }
            else -> throw IllegalArgumentException()
        }.buildAlignment(geneFeature, relativeTo)?.alignment
    }

    val nMutationsCount: Int
        get() =
            VPartsN.mutations.values.sumOf { it.size() } + NDNMutations.size() + JPartsN.mutations.values.sumOf { it.size() }

    fun aaAlignment(geneFeature: GeneFeature, relativeTo: GeneFeature = geneFeature): Alignment<AminoAcidSequence>? {
        validateArgs(geneFeature, relativeTo)
        checkAAComparable(geneFeature)
        return when (geneFeature.geneType) {
            Variable -> VPartsAA
            Joining -> JPartsAA
            null -> {
                check(geneFeature == relativeTo)
                checkNotNull(intersection(geneFeature, VJJunction))
                fullLengthPartsAA
            }
            else -> throw IllegalArgumentException()
        }.buildAlignment(geneFeature, relativeTo)?.alignment
    }

    fun aaMutationsDetailed(
        geneFeature: GeneFeature,
        relativeTo: GeneFeature = geneFeature
    ): Array<MutationsUtil.MutationNt2AADescriptor>? {
        validateArgs(geneFeature, relativeTo)
        checkAAComparable(geneFeature)
        val result = when (geneFeature.geneType) {
            Variable -> VPartsN
            Joining -> JPartsN
            null -> {
                check(geneFeature == relativeTo)
                checkNotNull(intersection(geneFeature, VJJunction))
                fullLengthPartsN
            }
            else -> throw IllegalArgumentException()
        }.buildAlignment(geneFeature, relativeTo) ?: return null
        return MutationsUtil.nt2aaDetailed(
            result.alignment.sequence1,
            result.alignment.absoluteMutations,
            result.translationParameters,
            maxShiftedTriplets
        )
    }

    fun differenceWith(other: MutationsDescription): MutationsDescription {
        check(VPartsN.mutations.keys == other.VPartsN.mutations.keys)
        check(JPartsN.mutations.keys == other.JPartsN.mutations.keys)
        return MutationsDescription(
            VPartsN = VPartsN.differenceWith(other.VPartsN),
            baseNDN = NDNMutations.mutate(baseNDN),
            NDNMutations = NDNMutations.invert().combineWith(other.NDNMutations),
            JPartsN = JPartsN.differenceWith(other.JPartsN),
            maxShiftedTriplets = maxShiftedTriplets,
            VPartsAA = { this@MutationsDescription.VPartsAA.differenceWith(other.VPartsAA) },
            JPartsAA = { this@MutationsDescription.JPartsAA.differenceWith(other.JPartsAA) },
            fullLengthPartsAA = { this@MutationsDescription.fullLengthPartsAA.differenceWith(other.fullLengthPartsAA) }
        )
    }

    private fun <S : Sequence<S>> Parts<S>.differenceWith(other: Parts<S>) =
        Parts(
            partitioning.applyMutations(combinedMutations),
            combinedMutations.mutate(sequence1),
            mutations.mapValuesTo(TreeMap()) { (geneFeature, mutations) ->
                mutations.invert().combineWith(other.mutations[geneFeature]!!)
            }
        )

    private fun <S : Sequence<S>> Parts<S>.buildAlignment(
        geneFeature: GeneFeature,
        relativeTo: GeneFeature
    ): Result<S>? {
        val alignmentsOfIntersections = mutations.mapNotNull { (key, value) ->
            intersection(key, geneFeature)?.let { intersection ->
                val shift = partitioning.getPosition(relativeTo.firstPoint)
                val sequence1Range = partitioning.getRange(intersection)
                val isLeftBoundOfPart = intersection.firstPoint == key.firstPoint
                val resultMutations = value.extractAbsoluteMutations(
                    sequence1Range,
                    isIncludeFirstInserts = isLeftBoundOfPart
                )
                Result(
                    Alignment(
                        sequence1.getRange(
                            shift,
                            //also adjust right position by relative gene feature
                            partitioning.getPosition(relativeTo.lastPoint)
                        ),
                        resultMutations.move(-shift),
                        sequence1Range.move(-shift),
                        MutationsUtils.projectRange(resultMutations, sequence1Range).move(-shift),
                        Float.NaN
                    ),
                    intersection,
                    partitioning
                )
            }
        }
        require(alignmentsOfIntersections.size <= 1) {
            "Can't build single intersection of $geneFeature and ${mutations.keys}"
        }
        return alignmentsOfIntersections.firstOrNull()
    }

    private data class Result<S : Sequence<S>>(
        val alignment: Alignment<S>,
        val geneFeature: GeneFeature,
        val partitioning: ExtendedReferencePoints
    )

    private val Result<NucleotideSequence>.translationParameters: TranslationParameters
        get() {
            checkAAComparable(geneFeature)
            return partitioning.getTranslationParameters(geneFeature)
        }


    private fun validateArgs(geneFeature: GeneFeature, relativeTo: GeneFeature) {
        require(!geneFeature.isComposite) {
            "Composite features are not supported: ${GeneFeature.encode(geneFeature)}"
        }
        require(relativeTo.firstPoint <= geneFeature.firstPoint) {
            "Base feature ${GeneFeature.encode(geneFeature)} can't be aligned to relative feature ${
                GeneFeature.encode(relativeTo)
            }"
        }
        if (geneFeature.geneType == null) {
            requireNotNull(intersection(geneFeature, VJJunction)) {
                "Algorithm doesn't support $${GeneFeature.encode(geneFeature)}"
            }
            require(geneFeature == relativeTo) {
                "Can't calculate alignment $geneFeature relative to $relativeTo"
            }
        }
    }
}

private fun checkAAComparable(geneFeature: GeneFeature) {
    require(!geneFeature.isAlignmentAttached) {
        "Can't construct amino acids for ${GeneFeature.encode(geneFeature)}"
    }
}


fun ReferencePoints.withVCDR3PartLength(VPartInCDR3Length: Int): ExtendedReferencePoints =
    ExtendedReferencePointsBuilder().also { builder ->
        builder.setPositionsFrom(this)
        builder.setPosition(VEndTrimmed, getPosition(CDR3Begin) + VPartInCDR3Length)
    }.build()

fun ReferencePoints.withJCDR3PartLength(JPartInCDR3Length: Int): ExtendedReferencePoints =
    ExtendedReferencePointsBuilder().also { builder ->
        builder.setPositionsFrom(this)
        builder.setPosition(JBeginTrimmed, getPosition(CDR3End) - JPartInCDR3Length)
    }.build()
