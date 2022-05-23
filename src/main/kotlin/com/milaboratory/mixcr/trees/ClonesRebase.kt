@file:Suppress("FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
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
                    VScoring,
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
                JScoring,
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
            NDNScoring,
            rootInfo.reconstructedNDN,
            mutationsFromVJGermline.knownNDN.getRange(NDNRangeInKnownNDN)
        ).absoluteMutations
        return CloneWithMutationsFromReconstructedRoot(
            MutationsSet(
                VGeneMutations(
                    VSequence1,
                    mutationsFromVJGermline.VMutations.mutations,
                    VMutationsInCDR3WithoutNDN
                ),
                NDNMutations(
                    rootInfo.reconstructedNDN,
                    NDNMutations
                ),
                JGeneMutations(
                    JSequence1,
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
        val originalKnownNDN = originalNode.NDNMutations.buildSequence()
        val VMutationsInCDR3WithoutNDN = if (originalRoot.VRangeInCDR3.length() < rebaseTo.VRangeInCDR3.length()) {
            val difference = Range(originalRoot.VRangeInCDR3.upper, rebaseTo.VRangeInCDR3.upper)
            originalNode.VMutations.partInCDR3
                .combineWithMutationsToTheRight(
                    difference,
                    Aligner.alignGlobal(
                        VScoring,
                        VSequence1,
                        originalKnownNDN,
                        difference.lower,
                        difference.length(),
                        0,
                        difference.length()
                    ).absoluteMutations
                )
        } else if (rebaseTo.VRangeInCDR3.length() < originalRoot.VRangeInCDR3.length()) {
            PartInCDR3(
                rebaseTo.VRangeInCDR3,
                originalNode.VMutations.partInCDR3.mutations
                    .extractAbsoluteMutations(rebaseTo.VRangeInCDR3, false)
            )
        } else {
            originalNode.VMutations.partInCDR3
        }
        val knownNDNBuilder = NucleotideSequence.ALPHABET.createBuilder()
        if (rebaseTo.VRangeInCDR3.length() < originalRoot.VRangeInCDR3.length()) {
            val VRangeInNDN = Range(rebaseTo.VRangeInCDR3.upper, originalRoot.VRangeInCDR3.upper)
            knownNDNBuilder.append(
                MutationsUtils.buildSequence(
                    VSequence1,
                    originalNode.VMutations.partInCDR3.mutations
                        .extractAbsoluteMutations(VRangeInNDN, false),
                    VRangeInNDN
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
                    originalNode.JMutations.partInCDR3.mutations,
                    Range(originalRoot.JRangeInCDR3.lower, rebaseTo.JRangeInCDR3.lower)
                )
            )
        }
        val rebasedKnownNDN = knownNDNBuilder.createAndDestroy()
        val JMutationsInCDR3WithoutNDN = if (originalRoot.JRangeInCDR3.length() < rebaseTo.JRangeInCDR3.length()) {
            val difference = Range(rebaseTo.JRangeInCDR3.lower, originalRoot.JRangeInCDR3.lower)
            originalNode.JMutations.partInCDR3
                .combineWithMutationsToTheLeft(
                    difference,
                    Aligner.alignGlobal(
                        JScoring,
                        JSequence1,
                        originalKnownNDN,
                        difference.lower,
                        difference.length(),
                        originalKnownNDN.size() - difference.length(),
                        difference.length()
                    ).absoluteMutations
                )
        } else if (rebaseTo.JRangeInCDR3.length() < originalRoot.JRangeInCDR3.length()) {
            PartInCDR3(
                rebaseTo.JRangeInCDR3,
                originalNode.JMutations.partInCDR3.mutations
                    .extractAbsoluteMutations(rebaseTo.JRangeInCDR3, false)
            )
        } else {
            originalNode.JMutations.partInCDR3
        }
        return MutationsSet(
            originalNode.VMutations.copy(
                partInCDR3 = VMutationsInCDR3WithoutNDN
            ),
            NDNMutations(
                rebaseTo.reconstructedNDN,
                Aligner.alignGlobal(
                    NDNScoring,
                    rebaseTo.reconstructedNDN,
                    rebasedKnownNDN
                ).absoluteMutations
            ),
            originalNode.JMutations.copy(
                partInCDR3 = JMutationsInCDR3WithoutNDN
            )
        )
    }

}

fun NDNRangeInKnownNDN(mutations: MutationsFromVJGermline, VRangeInCDR3: Range, JRangeInCDR3: Range): Range =
    Range(
        VRangeInCDR3.length() - mutations.VMutations.partInCDR3.range.length(),
        mutations.knownNDN.size() - (JRangeInCDR3.length() - mutations.JMutations.partInCDR3.range.length())
    )
