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
        var VMutationsInCDR3WithoutNDN = mutationsFromVJGermline.knownMutationsWithinCDR3.V
            .first
            .extractAbsoluteMutations(rootInfo.rangeInCDR3.V, true)
        if (rootInfo.rangeInCDR3.V.length() > mutationsFromVJGermline.knownMutationsWithinCDR3.V.second.length()) {
            val rangeToAlign = Range(
                mutationsFromVJGermline.knownMutationsWithinCDR3.V.second.upper,
                rootInfo.rangeInCDR3.V.upper
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

        var JMutationsInCDR3WithoutNDN = mutationsFromVJGermline.knownMutationsWithinCDR3.J
            .first
            .extractAbsoluteMutations(rootInfo.rangeInCDR3.J, true)
        if (rootInfo.rangeInCDR3.J.length() > mutationsFromVJGermline.knownMutationsWithinCDR3.J.second.length()) {
            val rangeToAlign = Range(
                rootInfo.rangeInCDR3.J.lower,
                mutationsFromVJGermline.knownMutationsWithinCDR3.J.second.lower
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

        val NDNRangeToCompare = Range(
            rootInfo.rangeInCDR3.V.length() + VMutationsInCDR3WithoutNDN.lengthDelta,
            mutationsFromVJGermline.CDR3.size() - rootInfo.rangeInCDR3.J.length() - JMutationsInCDR3WithoutNDN.lengthDelta
        )
        val compareWithNDN = when {
            NDNRangeToCompare.isReverse || NDNRangeToCompare.isEmpty -> NucleotideSequence.EMPTY
            else -> mutationsFromVJGermline.CDR3.getRange(NDNRangeToCompare)
        }
        val NDNMutations = Aligner.alignGlobal(
            scoringSet.NDN.scoring,
            rootInfo.reconstructedNDN,
            compareWithNDN
        ).absoluteMutations
        return CloneWithMutationsFromReconstructedRoot(
            MutationsSet(
                VGeneMutations(
                    mutationsFromVJGermline.mutations.V,
                    PartInCDR3(rootInfo.rangeInCDR3.V, VMutationsInCDR3WithoutNDN)
                ),
                NDNMutations(NDNMutations),
                JGeneMutations(
                    PartInCDR3(rootInfo.rangeInCDR3.J, JMutationsInCDR3WithoutNDN),
                    mutationsFromVJGermline.mutations.J
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
            originalRoot.rangeInCDR3.V.lower,
            min(originalRoot.rangeInCDR3.V.upper, rebaseTo.rangeInCDR3.V.upper)
        )
        val commonJRange = Range(
            max(originalRoot.rangeInCDR3.J.lower, rebaseTo.rangeInCDR3.J.lower),
            rebaseTo.rangeInCDR3.J.upper
        )

        val VRangeLeft = originalRoot.rangeInCDR3.V.setLower(commonVRange.upper)
        val JRangeLeft = originalRoot.rangeInCDR3.J.setUpper(commonJRange.lower)
        val toAlign = MutationsUtils.buildSequence(
            VSequence1,
            originalNode.mutations.V.partInCDR3.mutations.extractAbsoluteMutations(VRangeLeft, false),
            VRangeLeft
        ) + originalNode.NDNMutations.buildSequence(originalRoot) +
                MutationsUtils.buildSequence(
                    JSequence1,
                    originalNode.mutations.J.partInCDR3.mutations.extractAbsoluteMutations(JRangeLeft, true),
                    JRangeLeft
                )

        val VRangeToAlign = rebaseTo.rangeInCDR3.V.setLower(commonVRange.upper)
        val JRangeToAlign = rebaseTo.rangeInCDR3.J.setUpper(commonJRange.lower)
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
                mutations = originalNode.mutations.V.mutationsOutsideOfCDR3,
                partInCDR3 = PartInCDR3(
                    rebaseTo.rangeInCDR3.V,
                    originalNode.mutations.V.partInCDR3.mutations.extractAbsoluteMutations(commonVRange, false)
                        .concat(VMutationsToAdd)
                )
            ),
            NDNMutations(NDNMutations),
            JGeneMutations(
                mutations = originalNode.mutations.J.mutationsOutsideOfCDR3,
                partInCDR3 = PartInCDR3(
                    rebaseTo.rangeInCDR3.J,
                    JMutationsToAdd.concat(
                        originalNode.mutations.J.partInCDR3.mutations.extractAbsoluteMutations(commonJRange, false)
                    )
                )
            )
        )
    }
}
