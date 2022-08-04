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
import com.milaboratory.mixcr.util.plus

class ClonesRebase(
    private val sequence1: VJPair<NucleotideSequence>,
    private val scoringSet: ScoringSet
) {
    fun rebaseClone(
        rootInfo: RootInfo,
        mutationsFromVJGermline: MutationsFromVJGermline,
        cloneWrapper: CloneWrapper
    ): CloneWithMutationsFromReconstructedRoot {
        val VMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
            scoringSet.V.scoring,
            sequence1.V,
            mutationsFromVJGermline.CDR3,
            rootInfo.rangeInCDR3.V.lower,
            rootInfo.rangeInCDR3.V.length(),
            0,
            rootInfo.rangeInCDR3.V.length()
        ).absoluteMutations

        val JMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
            scoringSet.J.scoring,
            sequence1.J,
            mutationsFromVJGermline.CDR3,
            rootInfo.rangeInCDR3.J.lower,
            rootInfo.rangeInCDR3.J.length(),
            mutationsFromVJGermline.CDR3.size() - rootInfo.rangeInCDR3.J.length(),
            rootInfo.rangeInCDR3.J.length()
        ).absoluteMutations

        val NDNRangeToCompare = Range(
            rootInfo.rangeInCDR3.V.length() + VMutationsInCDR3WithoutNDN.lengthDelta,
            mutationsFromVJGermline.CDR3.size() - rootInfo.rangeInCDR3.J.length() - JMutationsInCDR3WithoutNDN.lengthDelta
        )
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
        val CDR3 = originalNode.buildCDR3(originalRoot)

        val VMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
            scoringSet.V.scoring,
            sequence1.V,
            CDR3,
            rebaseTo.rangeInCDR3.V.lower,
            rebaseTo.rangeInCDR3.V.length(),
            0,
            rebaseTo.rangeInCDR3.V.length()
        ).absoluteMutations
        val JMutationsInCDR3WithoutNDN = Aligner.alignGlobal(
            scoringSet.J.scoring,
            sequence1.J,
            CDR3,
            rebaseTo.rangeInCDR3.J.lower,
            rebaseTo.rangeInCDR3.J.length(),
            CDR3.size() - rebaseTo.rangeInCDR3.J.length(),
            rebaseTo.rangeInCDR3.J.length()
        ).absoluteMutations

        val NDNRangeToCompare = Range(
            rebaseTo.rangeInCDR3.V.length() + VMutationsInCDR3WithoutNDN.lengthDelta,
            CDR3.size() - rebaseTo.rangeInCDR3.J.length() - JMutationsInCDR3WithoutNDN.lengthDelta
        )
        check(!NDNRangeToCompare.isReverse) {
            "V CDR3 part and J CDR3 part intersect in ${originalRoot.VJBase}"
        }
        val compareWithNDN = when {
            NDNRangeToCompare.isEmpty -> NucleotideSequence.EMPTY
            else -> CDR3.getRange(NDNRangeToCompare)
        }
        val NDNMutations = Aligner.alignGlobal(
            scoringSet.NDN.scoring,
            rebaseTo.reconstructedNDN,
            compareWithNDN
        ).absoluteMutations

        val result = MutationsSet(
            VGeneMutations(
                mutations = originalNode.mutations.V.mutationsOutsideOfCDR3,
                partInCDR3 = PartInCDR3(
                    rebaseTo.rangeInCDR3.V,
                    VMutationsInCDR3WithoutNDN
                )
            ),
            NDNMutations(NDNMutations),
            JGeneMutations(
                mutations = originalNode.mutations.J.mutationsOutsideOfCDR3,
                partInCDR3 = PartInCDR3(
                    rebaseTo.rangeInCDR3.J,
                    JMutationsInCDR3WithoutNDN
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
