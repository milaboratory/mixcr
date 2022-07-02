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
@file:Suppress("FunctionName", "LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.plus
import kotlin.math.max
import kotlin.math.min

class ClonesRebase(
    private val VSequence1: NucleotideSequence,
    private val JSequence1: NucleotideSequence,
    private val scoringSet: ScoringSet
) {
    fun rebaseClone(
        rootInfo: RootInfo,
        mutationsFromVJGermline: MutationsFromVJGermline,
        cloneWrapper: CloneWrapper
    ): CloneWithMutationsFromReconstructedRoot {
        var NDNRangeInKnownNDN =
            NDNRangeInKnownNDN(mutationsFromVJGermline, rootInfo.VRangeInCDR3, rootInfo.JRangeInCDR3)
        var VMutationsInCDR3WithoutNDN = mutationsFromVJGermline.VMutations.partInCDR3
        val wasVRangeInCDR3 = mutationsFromVJGermline.VMutations.partInCDR3.range
        val VRange = Range(wasVRangeInCDR3.lower, rootInfo.VRangeInCDR3.upper)
        //can skip empty VRange because we will not include first mutations (empty range always will mutate to empty range)
        if (!VRange.isEmpty) {
            var VMutationsWithinNDNRange = mutationsFromVJGermline.knownVMutationsWithinNDN.second.intersection(VRange)
            VMutationsWithinNDNRange = VMutationsWithinNDNRange ?: Range(wasVRangeInCDR3.upper, wasVRangeInCDR3.upper)
            var lengthDelta = 0
            if (!VMutationsWithinNDNRange.isEmpty) {
                val VMutationsToAdd = mutationsFromVJGermline.knownVMutationsWithinNDN.first
                    .extractAbsoluteMutations(VMutationsWithinNDNRange, false)
                VMutationsInCDR3WithoutNDN = VMutationsInCDR3WithoutNDN
                    .combineWithMutationsToTheRight(VMutationsWithinNDNRange, VMutationsToAdd)
                lengthDelta += VMutationsToAdd.lengthDelta
            }
            val rangeToAlign = Range(VMutationsWithinNDNRange.upper, rootInfo.VRangeInCDR3.upper)
            if (!rangeToAlign.isEmpty && !rangeToAlign.isReverse) {
                val absoluteMutations = Aligner.alignGlobal(
                    scoringSet.VScoring,
                    VSequence1,
                    mutationsFromVJGermline.knownNDN,
                    rangeToAlign.lower,
                    rangeToAlign.length(),
                    VMutationsWithinNDNRange.length() + lengthDelta,
                    rangeToAlign.length()
                ).absoluteMutations
                VMutationsInCDR3WithoutNDN = VMutationsInCDR3WithoutNDN
                    .combineWithMutationsToTheRight(rangeToAlign, absoluteMutations)
            }
            NDNRangeInKnownNDN = Range(NDNRangeInKnownNDN.lower + lengthDelta, NDNRangeInKnownNDN.upper)
        }
        var JMutationsInCDR3WithoutNDN = mutationsFromVJGermline.JMutations.partInCDR3
        val wasJRangeInCDR3 = mutationsFromVJGermline.JMutations.partInCDR3.range
        val JRange = Range(rootInfo.JRangeInCDR3.lower, wasJRangeInCDR3.lower)
        var JMutationsWithinNDNRange = mutationsFromVJGermline.knownJMutationsWithinNDN.second
            .intersection(JRange)
        JMutationsWithinNDNRange = JMutationsWithinNDNRange ?: Range(wasJRangeInCDR3.lower, wasJRangeInCDR3.lower)
        var lengthDelta = 0
        val JMutationsToAdd = mutationsFromVJGermline.knownJMutationsWithinNDN.first
            .extractAbsoluteMutations(JMutationsWithinNDNRange, true)
        JMutationsInCDR3WithoutNDN = JMutationsInCDR3WithoutNDN.withoutLeftInsert()
            .combineWithMutationsToTheLeft(JMutationsWithinNDNRange, JMutationsToAdd)
        lengthDelta += JMutationsToAdd.lengthDelta
        val rangeToAlign = Range(rootInfo.JRangeInCDR3.lower, JMutationsWithinNDNRange.lower)
        if (!rangeToAlign.isEmpty && !rangeToAlign.isReverse) {
            val absoluteMutations = Aligner.alignGlobal(
                scoringSet.JScoring,
                JSequence1,
                mutationsFromVJGermline.knownNDN,
                rangeToAlign.lower,
                rangeToAlign.length(),
                NDNRangeInKnownNDN.upper - lengthDelta,
                rangeToAlign.length()
            ).absoluteMutations
            JMutationsInCDR3WithoutNDN =
                JMutationsInCDR3WithoutNDN.combineWithMutationsToTheLeft(rangeToAlign, absoluteMutations)
        }
        NDNRangeInKnownNDN = Range(NDNRangeInKnownNDN.lower, NDNRangeInKnownNDN.upper - lengthDelta)
        val NDNMutations = Aligner.alignGlobal(
            scoringSet.NDNScoring,
            rootInfo.reconstructedNDN,
            mutationsFromVJGermline.knownNDN.getRange(NDNRangeInKnownNDN)
        ).absoluteMutations
        return CloneWithMutationsFromReconstructedRoot(
            MutationsSet(
                VGeneMutations(
                    mutationsFromVJGermline.VMutations.mutations,
                    VMutationsInCDR3WithoutNDN
                ),
                NDNMutations(NDNMutations),
                JGeneMutations(
                    JMutationsInCDR3WithoutNDN,
                    mutationsFromVJGermline.JMutations.mutations
                )
            ),
            mutationsFromVJGermline,
            cloneWrapper
        )
    }

    fun rebaseMutations(
        originalNode: MutationsSet,
        originalRoot: RootInfo,
        rebaseTo: RootInfo
    ): MutationsSet {
        val commonVRange = Range(
            originalRoot.VRangeInCDR3.lower,
            min(originalRoot.VRangeInCDR3.upper, rebaseTo.VRangeInCDR3.upper)
        )
        val commonJRange = Range(
            max(originalRoot.JRangeInCDR3.lower, rebaseTo.JRangeInCDR3.lower),
            rebaseTo.JRangeInCDR3.upper
        )

        val VRangeLeft = originalRoot.VRangeInCDR3.setLower(commonVRange.upper)
        val JRangeLeft = originalRoot.JRangeInCDR3.setUpper(commonJRange.lower)
        val toAlign = MutationsUtils.buildSequence(
            VSequence1,
            originalNode.VMutations.partInCDR3.mutations.extractAbsoluteMutations(VRangeLeft, false),
            VRangeLeft
        ) + originalNode.NDNMutations.buildSequence(originalRoot) +
                MutationsUtils.buildSequence(
                    JSequence1,
                    originalNode.JMutations.partInCDR3.mutations.extractAbsoluteMutations(JRangeLeft, true),
                    JRangeLeft
                )

        val VRangeToAlign = rebaseTo.VRangeInCDR3.setLower(commonVRange.upper)
        val JRangeToAlign = rebaseTo.JRangeInCDR3.setUpper(commonJRange.lower)
        //TODO try to align with fewer indels
        val part =
            toAlign.size() / (VRangeToAlign.length() + rebaseTo.reconstructedNDN.size() + JRangeToAlign.length()).toDouble()
        val VPartToAlign = Range(0, (VRangeToAlign.length() * part).toInt())
        val JPartToAlign = Range((toAlign.size() - JRangeToAlign.length() * part).toInt(), toAlign.size())
        val NDNPartToAlign = Range(VPartToAlign.upper, JPartToAlign.lower)

        val VMutationsToAdd = Aligner.alignGlobal(
            scoringSet.VScoring,
            VSequence1,
            toAlign,
            VRangeToAlign.lower,
            VRangeToAlign.length(),
            VPartToAlign.lower,
            VPartToAlign.length()
        ).absoluteMutations

        val JMutationsToAdd = Aligner.alignGlobal(
            scoringSet.JScoring,
            JSequence1,
            toAlign,
            JRangeToAlign.lower,
            JRangeToAlign.length(),
            JPartToAlign.lower,
            JPartToAlign.length()
        ).absoluteMutations

        val NDNMutations = Aligner.alignGlobal(
            scoringSet.NDNScoring,
            rebaseTo.reconstructedNDN,
            toAlign,
            0,
            rebaseTo.reconstructedNDN.size(),
            NDNPartToAlign.lower,
            NDNPartToAlign.length()
        ).absoluteMutations

        return MutationsSet(
            VGeneMutations(
                mutations = originalNode.VMutations.mutations,
                partInCDR3 = PartInCDR3(
                    rebaseTo.VRangeInCDR3,
                    originalNode.VMutations.partInCDR3.mutations.extractAbsoluteMutations(commonVRange, false)
                        .concat(VMutationsToAdd)
                )
            ),
            NDNMutations(NDNMutations),
            JGeneMutations(
                mutations = originalNode.JMutations.mutations,
                partInCDR3 = PartInCDR3(
                    rebaseTo.JRangeInCDR3,
                    JMutationsToAdd.concat(
                        originalNode.JMutations.partInCDR3.mutations.extractAbsoluteMutations(commonJRange, false)
                    )
                )
            )
        )
    }
}

fun NDNRangeInKnownNDN(mutations: MutationsFromVJGermline, VRangeInCDR3: Range, JRangeInCDR3: Range): Range =
    Range(
        VRangeInCDR3.length() - mutations.VMutations.partInCDR3.range.length(),
        mutations.knownNDN.size() - (JRangeInCDR3.length() - mutations.JMutations.partInCDR3.range.length())
    )
