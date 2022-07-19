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
        var VMutationsInCDR3WithoutNDN = mutationsFromVJGermline.knownVMutationsWithinCDR3
            .first
            .extractAbsoluteMutations(rootInfo.VRangeInCDR3, true)
        if (rootInfo.VRangeInCDR3.length() > mutationsFromVJGermline.knownVMutationsWithinCDR3.second.length()) {
            val rangeToAlign = Range(
                mutationsFromVJGermline.knownVMutationsWithinCDR3.second.upper,
                rootInfo.VRangeInCDR3.upper
            )
            val additionalVMutations = Aligner.alignGlobal(
                scoringSet.V.scoring,
                VSequence1,
                mutationsFromVJGermline.CDR3,
                rangeToAlign.lower,
                rangeToAlign.length(),
                mutationsFromVJGermline.VEndTrimmedPosition + VMutationsInCDR3WithoutNDN.lengthDelta,
                rangeToAlign.length()
            ).absoluteMutations
            VMutationsInCDR3WithoutNDN = VMutationsInCDR3WithoutNDN.concat(additionalVMutations)
        }

        var JMutationsInCDR3WithoutNDN = mutationsFromVJGermline.knownJMutationsWithinCDR3
            .first
            .extractAbsoluteMutations(rootInfo.JRangeInCDR3, true)
        if (rootInfo.JRangeInCDR3.length() > mutationsFromVJGermline.knownJMutationsWithinCDR3.second.length()) {
            val rangeToAlign = Range(
                rootInfo.JRangeInCDR3.lower,
                mutationsFromVJGermline.knownJMutationsWithinCDR3.second.lower
            )
            val additionalJMutations = Aligner.alignGlobal(
                scoringSet.J.scoring,
                JSequence1,
                mutationsFromVJGermline.CDR3,
                rangeToAlign.lower,
                rangeToAlign.length(),
                mutationsFromVJGermline.JBeginTrimmedPosition - rangeToAlign.length() - JMutationsInCDR3WithoutNDN.lengthDelta,
                rangeToAlign.length()
            ).absoluteMutations
            JMutationsInCDR3WithoutNDN = additionalJMutations.concat(JMutationsInCDR3WithoutNDN)
        }

        val NDNMutations = Aligner.alignGlobal(
            scoringSet.NDN.scoring,
            rootInfo.reconstructedNDN,
            mutationsFromVJGermline.CDR3.getRange(
                rootInfo.VRangeInCDR3.length() + VMutationsInCDR3WithoutNDN.lengthDelta,
                mutationsFromVJGermline.CDR3.size() - rootInfo.JRangeInCDR3.length() - JMutationsInCDR3WithoutNDN.lengthDelta
            )
        ).absoluteMutations
        return CloneWithMutationsFromReconstructedRoot(
            MutationsSet(
                VGeneMutations(
                    mutationsFromVJGermline.VMutations,
                    PartInCDR3(rootInfo.VRangeInCDR3, VMutationsInCDR3WithoutNDN)
                ),
                NDNMutations(NDNMutations),
                JGeneMutations(
                    PartInCDR3(rootInfo.JRangeInCDR3, JMutationsInCDR3WithoutNDN),
                    mutationsFromVJGermline.JMutations
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
            scoringSet.V.scoring,
            VSequence1,
            toAlign,
            VRangeToAlign.lower,
            VRangeToAlign.length(),
            VPartToAlign.lower,
            VPartToAlign.length()
        ).absoluteMutations

        val JMutationsToAdd = Aligner.alignGlobal(
            scoringSet.J.scoring,
            JSequence1,
            toAlign,
            JRangeToAlign.lower,
            JRangeToAlign.length(),
            JPartToAlign.lower,
            JPartToAlign.length()
        ).absoluteMutations

        val NDNMutations = Aligner.alignGlobal(
            scoringSet.NDN.scoring,
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
