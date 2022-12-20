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
@file:Suppress("LocalVariableName", "FunctionName", "PrivatePropertyName", "PropertyName")

package com.milaboratory.mixcr.trees

import com.milaboratory.app.ApplicationException
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Sequence
import com.milaboratory.core.sequence.TranslationParameters
import com.milaboratory.miplots.filterNotNull
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.plus
import io.repseq.core.ExtendedReferencePoints
import io.repseq.core.ExtendedReferencePointsBuilder
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.JCDR3Part
import io.repseq.core.GeneFeature.VCDR3Part
import io.repseq.core.GeneFeature.VJJunction
import io.repseq.core.GeneFeature.intersection
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.JBegin
import io.repseq.core.ReferencePoint.JBeginTrimmed
import io.repseq.core.ReferencePoint.VEnd
import io.repseq.core.ReferencePoint.VEndTrimmed
import io.repseq.core.ReferencePoints
import java.util.*

class MutationsDescription private constructor(
    private val VPartsN: Parts<NucleotideSequence>,
    private val CDR3PartsN: Parts<NucleotideSequence>,
    private val JPartsN: Parts<NucleotideSequence>,
    private val maxShiftedTriplets: Int = Int.MAX_VALUE,
    VPartsAaInit: () -> Parts<AminoAcidSequence>,
    JPartsAaInit: () -> Parts<AminoAcidSequence>,
    CDR3PartsAaInit: () -> Parts<AminoAcidSequence>,
) {
    private val VPartsAa: Parts<AminoAcidSequence> by lazy {
        VPartsAaInit()
    }

    private val JPartsAa: Parts<AminoAcidSequence> by lazy {
        JPartsAaInit()
    }

    private val CDR3PartsAa: Parts<AminoAcidSequence> by lazy {
        CDR3PartsAaInit()
    }

    private val fullLengthPartsN: Parts<NucleotideSequence> by lazy {
        combineFullLength(VPartsN, CDR3PartsN, JPartsN)
    }

    private val fullLengthPartsAa: Parts<AminoAcidSequence> by lazy {
        combineFullLength(VPartsAa, CDR3PartsAa, JPartsAa)
    }

    /**
     * Combine mutations from CDR3 and V,J parts (without CDR3). All positions will be calculated accordingly
     */
    private fun <S : Sequence<S>> combineFullLength(
        VParts: Parts<S>,
        CDR3Parts: Parts<S>,
        JParts: Parts<S>
    ): Parts<S> {
        val CDR3BeginPosition = VParts.partitioning.getPosition(CDR3Begin)
        val JPositionShift = CDR3BeginPosition + CDR3Parts.partitioning.getPosition(CDR3End) -
                JParts.partitioning.getPosition(CDR3End)
        val partitioningForFullSequence = ExtendedReferencePointsBuilder().apply {
            setPositionsFrom(VParts.partitioning.without(VEndTrimmed).without(VEnd))
            setPositionsFrom(CDR3Parts.partitioning.move(CDR3BeginPosition))
            setPositionsFrom(JParts.partitioning.without(JBegin).without(JBeginTrimmed).move(JPositionShift))
        }.build()
        val fullSequence1 = VParts.sequence1.getRange(0, CDR3BeginPosition) +
                CDR3Parts.sequence1 +
                JParts.sequence1.getRange(JParts.partitioning.getPosition(CDR3End), JParts.sequence1.size())
        val resultMutations: SortedMap<GeneFeature, Mutations<S>> = TreeMap()
        resultMutations += VParts.mutations.filterKeys { it != VCDR3Part }
        resultMutations += CDR3Parts.mutations
            .mapValues { (_, mutations) ->
                mutations.move(partitioningForFullSequence.getPosition(CDR3Begin))
            }
        resultMutations += JParts.mutations
            .filterKeys { it != JCDR3Part }
            .mapValues { (_, mutations) ->
                mutations.move(JPositionShift)
            }

        return Parts(
            partitioningForFullSequence,
            fullSequence1,
            resultMutations
        )
    }

    /**
     * All info about some part of sequence that needed to build alignment.
     */
    private data class Parts<S : Sequence<S>>(
        val partitioning: ExtendedReferencePoints,
        val sequence1: S,
        val mutations: SortedMap<GeneFeature, Mutations<S>>
    ) {
        /**
         * If geneFeature in the key contains `splitBy` than it will be split and mutations will be split accordingly
         */
        fun withSplittedMutations(splitBy: ReferencePoint): Parts<S> = copy(
            mutations = (getLeftPart(splitBy) + getRightPart(splitBy)).toSortedMap()
        )

        private fun getLeftPart(splitBy: ReferencePoint) = mutations
            .filterKeys { it.firstPoint < splitBy }
            .entries
            .associate { (geneFeature, mutations) ->
                if (splitBy in geneFeature) {
                    val cropped = GeneFeature(geneFeature.firstPoint, splitBy)
                    cropped to mutations
                        .extractAbsoluteMutations(partitioning.getRange(cropped), isIncludeFirstInserts = true)
                } else {
                    geneFeature to mutations
                }
            }

        private fun getRightPart(splitBy: ReferencePoint) = mutations
            .filterKeys { splitBy < it.lastPoint }
            .entries
            .associate { (geneFeature, mutations) ->
                if (splitBy in geneFeature) {
                    val cropped = GeneFeature(splitBy, geneFeature.lastPoint)
                    cropped to mutations
                        .extractAbsoluteMutations(partitioning.getRange(cropped), isIncludeFirstInserts = false)
                } else {
                    geneFeature to mutations
                }
            }

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
                checkNotNull(intersection(geneFeature, VJJunction))
                fullLengthPartsN
            }
            else -> throw IllegalArgumentException()
        }.buildAlignment(geneFeature, relativeTo)?.alignment
    }

    val nMutationsCount: Int
        get() =
            VPartsN.mutations.filterKeys { it != VCDR3Part }.values.sumOf { it.size() } +
                    CDR3PartsN.mutations.values.sumOf { it.size() } +
                    JPartsN.mutations.filterKeys { it != JCDR3Part }.values.sumOf { it.size() }

    fun aaAlignment(geneFeature: GeneFeature, relativeTo: GeneFeature = geneFeature): Alignment<AminoAcidSequence>? {
        validateArgs(geneFeature, relativeTo)
        checkAAComparable(geneFeature)
        return when (geneFeature.geneType) {
            Variable -> VPartsAa
            Joining -> JPartsAa
            null -> {
                checkNotNull(intersection(geneFeature, VJJunction))
                fullLengthPartsAa
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
            CDR3PartsN = CDR3PartsN.differenceWith(other.CDR3PartsN),
            JPartsN = JPartsN.differenceWith(other.JPartsN),
            VPartsAaInit = { VPartsAa.differenceWith(other.VPartsAa) },
            JPartsAaInit = { JPartsAa.differenceWith(other.JPartsAa) },
            CDR3PartsAaInit = { CDR3PartsAa.differenceWith(other.CDR3PartsAa) },
            maxShiftedTriplets = maxShiftedTriplets
        )
    }

    /**
     * Calculate difference in mutations and shift positions
     */
    private fun <S : Sequence<S>> Parts<S>.differenceWith(other: Parts<S>): Parts<S> {
        val partitioningBuilder = ExtendedReferencePointsBuilder()
        val resultMutations = TreeMap<GeneFeature, Mutations<S>>()
        var shift = 0
        var lastAddedPosition: ReferencePoint = mutations.firstKey().firstPoint
        //first position will not change by mutations
        partitioningBuilder.setPosition(lastAddedPosition, partitioning.getPosition(lastAddedPosition))
        for (geneFeature in mutations.keys) {
            //shift accordingly to mutations in previous features
            val combined = mutations[geneFeature]!!.invert().combineWith(other.mutations[geneFeature]!!).move(shift)
            //if just apply combinedMutations, insertions that were on boundary may be applied on wrong side of reference point
            partitioningBuilder.setPositionsFrom(
                //get all positions in geneFeature
                partitioning.cutBy(geneFeature)
                    //exclude first point if it was already proceeded
                    .without(lastAddedPosition)
                    //calculate new positions in resulting sequence
                    .applyMutations(mutations[geneFeature]!!)
                    //and shift accordingly to mutations in previous features
                    .move(shift)
            )
            shift += mutations[geneFeature]!!.lengthDelta
            lastAddedPosition = geneFeature.lastPoint
            resultMutations[geneFeature] = combined
        }
        val combinedMutations = mutations.values
            .reduce { acc, mutations -> acc.concat(mutations) }
        return Parts(
            partitioningBuilder.build(),
            combinedMutations.mutate(sequence1),
            resultMutations
        )
    }

    private fun <S : Sequence<S>> Parts<S>.buildAlignment(
        requestedGeneFeature: GeneFeature,
        relativeTo: GeneFeature
    ): Result<S>? {
        val interestedFeatures = mutations
            .mapValues { (key) ->
                intersection(key, requestedGeneFeature)
            }
            .filterNotNull()
        if (interestedFeatures.isEmpty()) return null
        val overallInterception = interestedFeatures.values.reduce { previous, next ->
            ApplicationException.check(previous.lastPoint == next.firstPoint) {
                "Can't build single intersection of $requestedGeneFeature and ${mutations.keys}"
            }
            GeneFeature(previous.firstPoint, next.lastPoint)
        }
        if (overallInterception != requestedGeneFeature) return null
        val alignmentsOfIntersections = mutations.mapNotNull { (key, value) ->
            interestedFeatures[key]?.let { intersection ->
                val sequence1Range = partitioning.getRange(intersection)
                val isLeftBoundOfPart = intersection.firstPoint == key.firstPoint
                val resultMutations = value.extractAbsoluteMutations(
                    sequence1Range,
                    isIncludeFirstInserts = isLeftBoundOfPart
                )
                val shift = partitioning.getPosition(relativeTo.firstPoint)
                //build alignment by `intersection`, but shift everything accordingly to `relativeTo`
                Alignment(
                    sequence1.getRange(partitioning.getRange(relativeTo)),
                    resultMutations.move(-shift),
                    sequence1Range.move(-shift),
                    MutationsUtils.projectRange(resultMutations, sequence1Range).move(-shift),
                    Float.NaN
                )
            }
        }
        //merge alignments for neighbor gene features
        val alignment = alignmentsOfIntersections
            .reduce { previousAlignment, nextAlignment ->
                val resultMutations = previousAlignment.absoluteMutations.concat(nextAlignment.absoluteMutations)
                val sequence1Range = previousAlignment.sequence1Range.setUpper(nextAlignment.sequence1Range.upper)
                Alignment(
                    previousAlignment.sequence1,
                    resultMutations,
                    sequence1Range,
                    MutationsUtils.projectRange(resultMutations, sequence1Range),
                    Float.NaN
                )
            }
        return Result(
            alignment,
            requestedGeneFeature,
            partitioning
        )
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
        }
    }

    companion object {
        operator fun invoke(
            VParts: SortedMap<GeneFeature, Mutations<NucleotideSequence>>,
            VSequence1: NucleotideSequence,
            VPartitioning: ExtendedReferencePoints,
            baseNDN: NucleotideSequence,
            NDNMutations: Mutations<NucleotideSequence>,
            JParts: SortedMap<GeneFeature, Mutations<NucleotideSequence>>,
            JSequence1: NucleotideSequence,
            JPartitioning: ExtendedReferencePoints,
            maxShiftedTriplets: Int = Int.MAX_VALUE
        ): MutationsDescription {
            check(VParts.lastKey().lastPoint == VEndTrimmed)
            check(JParts.firstKey().firstPoint == JBeginTrimmed)
            val VPartsN = Parts(VPartitioning, VSequence1, VParts).withSplittedMutations(CDR3Begin)
            val JPartsN = Parts(JPartitioning, JSequence1, JParts).withSplittedMutations(CDR3End)
            val CDR3PartsN = calculateCDR3PartsN(VPartsN, JPartsN, baseNDN, NDNMutations)
            return MutationsDescription(
                VPartsN = VPartsN,
                CDR3PartsN = CDR3PartsN,
                JPartsN = JPartsN,
                maxShiftedTriplets = maxShiftedTriplets,
                VPartsAaInit = { VPartsN.translate(maxShiftedTriplets) },
                JPartsAaInit = { JPartsN.translate(maxShiftedTriplets) },
                CDR3PartsAaInit = { CDR3PartsN.translate(maxShiftedTriplets) }
            )
        }

        /**
         * Combine mutations and other info from VCDR3Part, NDN and JCDR3Part
         */
        private fun calculateCDR3PartsN(
            VPartsN: Parts<NucleotideSequence>,
            JPartsN: Parts<NucleotideSequence>,
            baseNDN: NucleotideSequence,
            NDNMutations: Mutations<NucleotideSequence>
        ): Parts<NucleotideSequence> {
            val VCDR3PartN = VPartsN.sequence1.getRange(VPartsN.partitioning.getRange(VCDR3Part))
            val JCDR3PartN = JPartsN.sequence1.getRange(JPartsN.partitioning.getRange(JCDR3Part))
            val CDR3Sequence1 = VCDR3PartN + baseNDN + JCDR3PartN
            val partitioningForCDR3 = ExtendedReferencePointsBuilder().apply {
                setPosition(CDR3Begin, 0)
                setPosition(VEndTrimmed, VCDR3PartN.size())
                setPosition(
                    JBeginTrimmed,
                    CDR3Sequence1.size() - JCDR3PartN.size()
                )
                setPosition(CDR3End, CDR3Sequence1.size())
            }.build()
            val JMutationsShift = JPartsN.partitioning.getPosition(CDR3End) - JCDR3PartN.size()
            return Parts(
                partitioningForCDR3,
                CDR3Sequence1,
                sortedMapOf(
                    CDR3 to VPartsN.mutations[VCDR3Part]!!.move(-VPartsN.partitioning.getPosition(CDR3Begin))
                        .concat(NDNMutations.move(partitioningForCDR3.getPosition(VEndTrimmed)))
                        .concat(JPartsN.mutations[JCDR3Part]!!.move(partitioningForCDR3.getPosition(JBeginTrimmed) - JMutationsShift))
                )
            )
        }

        /**
         * Translate to AA (mutations, sequence1 and portioning)
         */
        private fun Parts<NucleotideSequence>.translate(maxShiftedTriplets: Int): Parts<AminoAcidSequence> {
            val anchorPointForTranslation = when {
                mutations.keys.any { CDR3Begin in it } -> CDR3Begin
                else -> CDR3End
            }
            val translationParameters = TranslationParameters.withIncompleteCodon(
                partitioning.getPosition(anchorPointForTranslation)
            )
            //for every position calculate AA position
            val newPartitioning = ExtendedReferencePointsBuilder().apply {
                for (i in 0 until partitioning.pointsCount()) {
                    val referencePoint = partitioning.referencePointFromIndex(i)
                    val position = partitioning.getPosition(referencePoint)
                    if (position >= 0) {
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
            }.build()
            return Parts(
                newPartitioning,
                AminoAcidSequence.translate(sequence1, translationParameters),
                mutations.mapValuesTo(TreeMap()) { (_, mutations) ->
                    MutationsUtil.nt2aa(
                        sequence1,
                        mutations,
                        translationParameters,
                        maxShiftedTriplets
                    )
                }
            )
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
