@file:Suppress("FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.RangeInfo
import kotlin.math.max

@Suppress("LocalVariableName")
class ClonesRebase(
    private val VSequence1: NucleotideSequence,
    private val VScoring: AlignmentScoring<NucleotideSequence>,
    private val NDNScoring: AlignmentScoring<NucleotideSequence>,
    private val JSequence1: NucleotideSequence,
    private val JScoring: AlignmentScoring<NucleotideSequence>
) {
    fun rebaseClone(
        rootInfo: RootInfo,
        mutationsFromVJGermline: MutationsFromVJGermline,
        cloneWrapper: CloneWrapper
    ): CloneWithMutationsFromReconstructedRoot {
        var NDNRangeInKnownNDN =
            NDNRangeInKnownNDN(mutationsFromVJGermline, rootInfo.VRangeInCDR3, rootInfo.JRangeInCDR3)
        var VMutationsInCDR3WithoutNDN = mutationsFromVJGermline.VMutationsInCDR3WithoutNDN
        val wasVRangeInCDR3 = mutationsFromVJGermline.VMutationsInCDR3WithoutNDN.rangeInfo.range
        val VRange = Range(wasVRangeInCDR3.lower, rootInfo.VRangeInCDR3.upper)
        //can skip empty VRange because we will not include first mutations (empty range always will mutate to empty range)
        if (!VRange.isEmpty) {
            var VMutationsWithinNDNRange = mutationsFromVJGermline.knownVMutationsWithinNDN.second.intersection(VRange)
            VMutationsWithinNDNRange = VMutationsWithinNDNRange ?: Range(wasVRangeInCDR3.upper, wasVRangeInCDR3.upper)
            var lengthDelta = 0
            if (!VMutationsWithinNDNRange.isEmpty) {
                val VMutationsToAdd = MutationsWithRange(
                    VSequence1,
                    mutationsFromVJGermline.knownVMutationsWithinNDN.first,
                    RangeInfo(VMutationsWithinNDNRange, false)
                )
                VMutationsInCDR3WithoutNDN =
                    VMutationsInCDR3WithoutNDN.combineWithTheSameMutationsRight(VMutationsToAdd)
                lengthDelta += VMutationsToAdd.lengthDelta()
            }
            val rangeToAlign = Range(VMutationsWithinNDNRange.upper, rootInfo.VRangeInCDR3.upper)
            if (!rangeToAlign.isEmpty && !rangeToAlign.isReverse) {
                val absoluteMutations = Aligner.alignGlobal(
                    VScoring,
                    VSequence1,
                    mutationsFromVJGermline.knownNDN,
                    rangeToAlign.lower,
                    rangeToAlign.length(),
                    VMutationsWithinNDNRange.length() + lengthDelta,
                    rangeToAlign.length()
                ).absoluteMutations
                VMutationsInCDR3WithoutNDN =
                    VMutationsInCDR3WithoutNDN.combineWithMutationsToTheRight(absoluteMutations, rangeToAlign)
            }
            NDNRangeInKnownNDN = Range(NDNRangeInKnownNDN.lower + lengthDelta, NDNRangeInKnownNDN.upper)
        }
        var JMutationsInCDR3WithoutNDN = mutationsFromVJGermline.JMutationsInCDR3WithoutNDN
        val wasJRangeInCDR3 = mutationsFromVJGermline.JMutationsInCDR3WithoutNDN.rangeInfo.range
        val JRange = Range(rootInfo.JRangeInCDR3.lower, wasJRangeInCDR3.lower)
        var JMutationsWithinNDNRange = mutationsFromVJGermline.knownJMutationsWithinNDN.second
            .intersection(JRange)
        JMutationsWithinNDNRange = JMutationsWithinNDNRange ?: Range(wasJRangeInCDR3.lower, wasJRangeInCDR3.lower)
        var lengthDelta = 0
        val JMutationsToAdd = MutationsWithRange(
            JSequence1,
            mutationsFromVJGermline.knownJMutationsWithinNDN.first,
            RangeInfo(JMutationsWithinNDNRange, true)
        )
        JMutationsInCDR3WithoutNDN = JMutationsInCDR3WithoutNDN.combineWithTheSameMutationsLeft(JMutationsToAdd)
        lengthDelta += JMutationsToAdd.lengthDelta()
        val rangeToAlign = Range(rootInfo.JRangeInCDR3.lower, JMutationsWithinNDNRange.lower)
        if (!rangeToAlign.isEmpty && !rangeToAlign.isReverse) {
            val absoluteMutations = Aligner.alignGlobal(
                JScoring,
                JSequence1,
                mutationsFromVJGermline.knownNDN,
                rangeToAlign.lower,
                rangeToAlign.length(),
                NDNRangeInKnownNDN.upper - lengthDelta,
                rangeToAlign.length()
            ).absoluteMutations
            JMutationsInCDR3WithoutNDN =
                JMutationsInCDR3WithoutNDN.combineWithMutationsToTheLeft(absoluteMutations, rangeToAlign)
        }
        NDNRangeInKnownNDN = Range(NDNRangeInKnownNDN.lower, NDNRangeInKnownNDN.upper - lengthDelta)
        val mutations = MutationsDescription(
            mutationsFromVJGermline.VMutationsWithoutCDR3,
            VMutationsInCDR3WithoutNDN,
            NDNMutations(
                rootInfo.reconstructedNDN,
                mutationsFromVJGermline.knownNDN.getRange(NDNRangeInKnownNDN)
            ),
            JMutationsInCDR3WithoutNDN,
            mutationsFromVJGermline.JMutationsWithoutCDR3
        )
        return CloneWithMutationsFromReconstructedRoot(mutations, mutationsFromVJGermline, cloneWrapper)
    }

    fun rebaseMutations(
        originalNode: MutationsDescription,
        originalRoot: RootInfo,
        rebaseTo: RootInfo
    ): MutationsDescription {
        val originalKnownNDN = originalNode.knownNDN.buildSequence()
        val VMutationsInCDR3WithoutNDN = if (originalRoot.VRangeInCDR3.length() < rebaseTo.VRangeInCDR3.length()) {
            val difference =
                Range(originalRoot.VRangeInCDR3.upper, rebaseTo.VRangeInCDR3.upper)
            originalNode.VMutationsInCDR3WithoutNDN
                .combineWithMutationsToTheRight(
                    Aligner.alignGlobal(
                        VScoring,
                        VSequence1,
                        originalKnownNDN,
                        difference.lower,
                        difference.length(),
                        0,
                        difference.length()
                    ).absoluteMutations,
                    difference
                )
        } else if (rebaseTo.VRangeInCDR3.length() < originalRoot.VRangeInCDR3.length()) {
            MutationsWithRange(
                VSequence1,
                originalNode.VMutationsInCDR3WithoutNDN.mutations,
                RangeInfo(
                    rebaseTo.VRangeInCDR3,
                    false
                )
            )
        } else {
            originalNode.VMutationsInCDR3WithoutNDN
        }
        val knownNDNBuilder = NucleotideSequence.ALPHABET.createBuilder()
        if (rebaseTo.VRangeInCDR3.length() < originalRoot.VRangeInCDR3.length()) {
            knownNDNBuilder.append(
                MutationsUtils.buildSequence(
                    VSequence1,
                    originalNode.VMutationsInCDR3WithoutNDN.mutations,
                    RangeInfo(
                        Range(rebaseTo.VRangeInCDR3.upper, originalRoot.VRangeInCDR3.upper),
                        false
                    )
                )
            )
        }
        knownNDNBuilder.append(
            originalKnownNDN.getRange(
                max(0, rebaseTo.VRangeInCDR3.length() - originalRoot.VRangeInCDR3.length()),
                originalKnownNDN.size() - max(0, rebaseTo.JRangeInCDR3.length() - originalRoot.JRangeInCDR3.length())
            )
        )
        if (rebaseTo.JRangeInCDR3.length() < originalRoot.JRangeInCDR3.length()) {
            knownNDNBuilder.append(
                MutationsUtils.buildSequence(
                    JSequence1,
                    originalNode.JMutationsInCDR3WithoutNDN.mutations,
                    RangeInfo(
                        Range(originalRoot.JRangeInCDR3.lower, rebaseTo.JRangeInCDR3.lower),
                        false
                    )
                )
            )
        }
        val rebasedKnownNDN = knownNDNBuilder.createAndDestroy()
        val JMutationsInCDR3WithoutNDN = if (originalRoot.JRangeInCDR3.length() < rebaseTo.JRangeInCDR3.length()) {
            val difference =
                Range(rebaseTo.JRangeInCDR3.lower, originalRoot.JRangeInCDR3.lower)
            originalNode.JMutationsInCDR3WithoutNDN
                .combineWithMutationsToTheLeft(
                    Aligner.alignGlobal(
                        JScoring,
                        JSequence1,
                        originalKnownNDN,
                        difference.lower,
                        difference.length(),
                        originalKnownNDN.size() - difference.length(),
                        difference.length()
                    ).absoluteMutations,
                    difference
                )
        } else if (rebaseTo.JRangeInCDR3.length() < originalRoot.JRangeInCDR3.length()) {
            MutationsWithRange(
                JSequence1,
                originalNode.JMutationsInCDR3WithoutNDN.mutations,
                RangeInfo(
                    rebaseTo.JRangeInCDR3,
                    false
                )
            )
        } else {
            originalNode.JMutationsInCDR3WithoutNDN
        }
        return MutationsDescription(
            originalNode.VMutationsWithoutCDR3,
            VMutationsInCDR3WithoutNDN,
            NDNMutations(rebaseTo.reconstructedNDN, rebasedKnownNDN),
            JMutationsInCDR3WithoutNDN,
            originalNode.JMutationsWithoutCDR3
        )
    }

    private fun NDNMutations(first: NucleotideSequence, second: NucleotideSequence): MutationsWithRange {
        return MutationsWithRange(
            first,
            Aligner.alignGlobal(
                NDNScoring,
                first,
                second
            ).absoluteMutations as Mutations<NucleotideSequence>,
            RangeInfo(Range(0, first.size()), true)
        )
    }
}

fun NDNRangeInKnownNDN(mutations: MutationsFromVJGermline, VRangeInCDR3: Range, JRangeInCDR3: Range): Range =
    Range(
        VRangeInCDR3.length() - mutations.VMutationsInCDR3WithoutNDN.rangeInfo.range.length(),
        mutations.knownNDN
            .size() - (JRangeInCDR3.length() - mutations.JMutationsInCDR3WithoutNDN.rangeInfo.range.length())
    )
