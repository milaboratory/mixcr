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
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.plus
import kotlin.math.max
import kotlin.math.min

class ClonesRebase(
    private val sequence1: VJPair<NucleotideSequence>,
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
            VMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
                scoringSet.V.scoring,
                sequence1.V,
                mutationsFromVJGermline.CDR3,
                rootInfo.rangeInCDR3.V.lower,
                rootInfo.rangeInCDR3.V.length(),
                0,
                rootInfo.rangeInCDR3.V.length()
            ).absoluteMutations
        }

        var JMutationsInCDR3WithoutNDN = mutationsFromVJGermline.knownMutationsWithinCDR3.J
            .first
            .extractAbsoluteMutations(rootInfo.rangeInCDR3.J, true)
        if (rootInfo.rangeInCDR3.J.length() > mutationsFromVJGermline.knownMutationsWithinCDR3.J.second.length()) {
            JMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
                scoringSet.J.scoring,
                sequence1.J,
                mutationsFromVJGermline.CDR3,
                rootInfo.rangeInCDR3.J.lower,
                rootInfo.rangeInCDR3.J.length(),
                mutationsFromVJGermline.CDR3.size() - rootInfo.rangeInCDR3.J.length(),
                rootInfo.rangeInCDR3.J.length()
            ).absoluteMutations
        }

        var NDNRangeToCompare = Range(
            rootInfo.rangeInCDR3.V.length() + VMutationsInCDR3WithoutNDN.lengthDelta,
            mutationsFromVJGermline.CDR3.size() - rootInfo.rangeInCDR3.J.length() - JMutationsInCDR3WithoutNDN.lengthDelta
        )
        //if NDN range was small or empty and germline mutations in VJ CDR3 parts increase they size,
        //we need to realign V and J parts to strictly they ranges
        if (NDNRangeToCompare.isReverse) {
            if (VMutationsInCDR3WithoutNDN.lengthDelta > 0) {
                VMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
                    scoringSet.V.scoring,
                    sequence1.V,
                    mutationsFromVJGermline.CDR3,
                    rootInfo.rangeInCDR3.V.lower,
                    rootInfo.rangeInCDR3.V.length(),
                    0,
                    rootInfo.rangeInCDR3.V.length()
                ).absoluteMutations
            }

            if (JMutationsInCDR3WithoutNDN.lengthDelta > 0) {
                JMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
                    scoringSet.J.scoring,
                    sequence1.J,
                    mutationsFromVJGermline.CDR3,
                    rootInfo.rangeInCDR3.J.lower,
                    rootInfo.rangeInCDR3.J.length(),
                    mutationsFromVJGermline.CDR3.size() - rootInfo.rangeInCDR3.J.length(),
                    rootInfo.rangeInCDR3.J.length()
                ).absoluteMutations
            }

            NDNRangeToCompare = Range(
                rootInfo.rangeInCDR3.V.length() + VMutationsInCDR3WithoutNDN.lengthDelta,
                mutationsFromVJGermline.CDR3.size() - rootInfo.rangeInCDR3.J.length() - JMutationsInCDR3WithoutNDN.lengthDelta
            )
        }
        check(!NDNRangeToCompare.isReverse) {
            "V CDR3 part and J CDR3 part intersect in ${cloneWrapper.VJBase}"
        }
        val compareWithNDN = when {
            NDNRangeToCompare.isEmpty -> NucleotideSequence.EMPTY
            else -> mutationsFromVJGermline.CDR3.getRange(NDNRangeToCompare)
        }
        val NDNMutations = Aligner.alignGlobal(
            scoringSet.NDN.scoring,
            rootInfo.reconstructedNDN,
            compareWithNDN
        ).absoluteMutations
        val result = CloneWithMutationsFromReconstructedRoot(
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
        check(result.mutationsFromVJGermline.CDR3 == result.mutationsSet.buildCDR3(rootInfo)) {
            "Rebased CDR3 not equal to original in ${cloneWrapper.VJBase}"
        }
        return result
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
            sequence1.V,
            originalNode.mutations.V.partInCDR3.mutations.extractAbsoluteMutations(VRangeLeft, false),
            VRangeLeft
        ) + originalNode.NDNMutations.buildSequence(originalRoot) +
                MutationsUtils.buildSequence(
                    sequence1.J,
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
            sequence1.V,
            toAlign,
            VRangeToAlign.lower,
            VRangeToAlign.length(),
            VPartToAlign.lower,
            VPartToAlign.length()
        ).absoluteMutations

        val JMutationsToAdd = Aligner.alignGlobal(
            scoringSet.J.scoring,
            sequence1.J,
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

        val result = MutationsSet(
            VGeneMutations(
                mutations = originalNode.mutations.V.mutationsOutsideOfCDR3,
                partInCDR3 = PartInCDR3(
                    rebaseTo.rangeInCDR3.V,
                    //TODO realign, don't add difference
                    originalNode.mutations.V.partInCDR3.mutations.extractAbsoluteMutations(commonVRange, false)
                        .concat(VMutationsToAdd)
                )
            ),
            NDNMutations(NDNMutations),
            JGeneMutations(
                mutations = originalNode.mutations.J.mutationsOutsideOfCDR3,
                partInCDR3 = PartInCDR3(
                    rebaseTo.rangeInCDR3.J,
                    //TODO realign, don't add difference
                    JMutationsToAdd.concat(
                        originalNode.mutations.J.partInCDR3.mutations.extractAbsoluteMutations(commonJRange, false)
                    )
                )
            )
        )
        check(originalNode.buildCDR3(originalRoot) == result.buildCDR3(rebaseTo)) {
            "Rebased CDR3 not equal to original"
        }
        return result
    }

    private fun MutationsSet.buildCDR3(rootInfo: RootInfo) = MutationsUtils.buildSequence(
        rootInfo.sequence1.V,
        mutations.V.partInCDR3.mutations,
        rootInfo.rangeInCDR3.V
    ) + NDNMutations.mutations.mutate(rootInfo.reconstructedNDN) +
            MutationsUtils.buildSequence(
                rootInfo.sequence1.J,
                mutations.J.partInCDR3.mutations,
                rootInfo.rangeInCDR3.J
            )
}
