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
@file:Suppress("FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.NucleotideAlphabet
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.NucleotideSequence.ALPHABET
import com.milaboratory.core.sequence.Wildcard
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.mixcr.util.intersection
import java.util.*
import kotlin.math.abs

internal object MutationsUtils {
    /**
     * Mutate and get result by range in original sequence
     */
    fun buildSequence(
        sequence1: NucleotideSequence,
        mutations: Mutations<NucleotideSequence>,
        range: Range
    ): NucleotideSequence = mutations.mutate(sequence1).getRange(projectRange(mutations, range))

    fun projectRange(mutations: Mutations<NucleotideSequence>, range: Range): Range {
        //for including inclusions before position one must step left before conversion and step right after
        val from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(range.lower)) -
            mutations.asSequence()
                .count { mutation -> Mutation.getPosition(mutation) == range.lower && Mutation.isInsertion(mutation) }
        val to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(range.upper))
        return Range(from, to)
    }

    fun mutationsBetween(rootInfo: RootInfo, first: MutationsSet, second: MutationsSet) = NodeMutationsDescription(
        mutationsBetween(rootInfo.VSequence, first.VMutations, second.VMutations),
        difference(
            first.VMutations.partInCDR3.mutations,
            second.VMutations.partInCDR3.mutations,
            rootInfo.VSequence,
            rootInfo.VRangeInCDR3
        ),
        difference(
            first.NDNMutations.mutations,
            second.NDNMutations.mutations,
            rootInfo.reconstructedNDN,
            Range(0, rootInfo.reconstructedNDN.size())
        ),
        difference(
            first.JMutations.partInCDR3.mutations,
            second.JMutations.partInCDR3.mutations,
            rootInfo.JSequence,
            rootInfo.JRangeInCDR3
        ),
        mutationsBetween(rootInfo.JSequence, first.JMutations, second.JMutations)
    )

    private fun mutationsBetween(
        sequence1: NucleotideSequence,
        firstMutations: GeneMutations,
        secondMutations: GeneMutations
    ): Map<Range, MutationsWithRange> =
        fold(firstMutations.mutations, secondMutations.mutations) { base, comparison, range ->
            difference(base, comparison, sequence1, range)
        }

    private fun difference(
        base: Mutations<NucleotideSequence>,
        comparison: Mutations<NucleotideSequence>,
        sequence: NucleotideSequence,
        range: Range = Range(0, sequence.size())
    ): MutationsWithRange {
        val sequence1 = buildSequence(sequence, base, range)
        return MutationsWithRange(
            sequence1,
            base.invert()
                .combineWith(comparison)
                .move(-range.lower),
            Range(0, sequence1.size())
        )
    }

    fun <R, V1, V2> fold(
        first: Map<Range, V1>,
        second: Map<Range, V2>,
        folder: (V1, V2, Range) -> R
    ): Map<Range, R> {
        require(first.keys == second.keys)
        return first.mapValues { (range, value) -> folder(value, second[range]!!, range) }
    }

    fun positionIfNucleotideWasDeleted(position: Int): Int = when {
        position < -1 -> abs(position + 1)
        (position == -1) -> 0
        else -> position
    }

    fun NDNScoring(): AlignmentScoring<NucleotideSequence> = AffineGapAlignmentScoring(
        ALPHABET,
        calculateSubstitutionMatrix(5, -4, 4, ALPHABET),
        -10,
        -1
    )

    @Suppress("SameParameterValue")
    private fun calculateSubstitutionMatrix(
        match: Int,
        mismatch: Int,
        multiplierOfAsymmetry: Int,
        alphabet: NucleotideAlphabet
    ): IntArray {
        val codes = alphabet.size()
        val matrix = IntArray(codes * codes)
        Arrays.fill(matrix, mismatch)
        for (i in 0 until codes) matrix[i + codes * i] = match
        return fillWildcardScoresMatches(matrix, alphabet, match, mismatch, multiplierOfAsymmetry)
    }

    private fun fillWildcardScoresMatches(
        matrix: IntArray,
        alphabet: NucleotideAlphabet,
        match: Int,
        mismatch: Int,
        multiplierOfAsymmetry: Int
    ): IntArray {
        val alSize = alphabet.size()
        require(matrix.size == alSize * alSize) { "Wrong matrix size." }

        //TODO remove excludeSet from milib
        for (wc1 in alphabet.allWildcards) for (wc2 in alphabet.allWildcards) {
            if (wc1.isBasic && wc2.isBasic) continue
            var sumScore = 0
            for (i in 0 until wc1.basicSize()) {
                sumScore += if (wc2.matches(wc1.getMatchingCode(i))) {
                    match
                } else {
                    mismatch
                }
            }
            for (i in 0 until wc2.basicSize()) {
                sumScore += if (wc1.matches(wc2.getMatchingCode(i))) {
                    match * multiplierOfAsymmetry
                } else {
                    mismatch * multiplierOfAsymmetry
                }
            }
            sumScore /= wc1.basicSize() + wc2.basicSize() * multiplierOfAsymmetry
            matrix[wc1.code + wc2.code * alSize] = sumScore
        }
        return matrix
    }

    //TODO removals and inserts
    fun concreteNDNChild(
        parent: Mutations<NucleotideSequence>,
        child: Mutations<NucleotideSequence>
    ): Mutations<NucleotideSequence> {
        val mutationsOfParentByPositions = parent.asSequence().groupBy { Mutation.getPosition(it) }
        val mutationsBuilder = MutationsBuilder(ALPHABET)
        child.asSequence().forEach { mutationOfChild ->
            if (Mutation.isInDel(mutationOfChild)) {
                mutationsBuilder.append(mutationOfChild)
            } else {
                val position = Mutation.getPosition(mutationOfChild)
                val mutationsOfParent =
                    mutationsOfParentByPositions[position]?.firstOrNull { Mutation.isSubstitution(it) }
                if (mutationsOfParent == null) {
                    mutationsBuilder.append(mutationOfChild)
                } else {
                    val from = Mutation.getFrom(mutationOfChild)
                    val to = concreteChild(Mutation.getTo(mutationsOfParent), Mutation.getTo(mutationOfChild))
                    if (from != to) {
                        mutationsBuilder.append(Mutation.createSubstitution(position, from.toInt(), to.toInt()))
                    }
                }
            }
        }
        return mutationsBuilder.createAndDestroy()
    }

    private fun concreteChild(parentSymbol: Byte, childSymbol: Byte): Byte = when {
        parentSymbol == childSymbol -> childSymbol
        ALPHABET.isWildcard(childSymbol) -> when {
            matchesStrictly(ALPHABET.codeToWildcard(childSymbol), parentSymbol) -> parentSymbol
            else -> {
                val basicMask = (ALPHABET.codeToWildcard(parentSymbol).basicMask
                    or ALPHABET.codeToWildcard(childSymbol).basicMask)
                ALPHABET.maskToWildcard(basicMask).code
            }
        }
        else -> childSymbol
    }

    //TODO removals and inserts
    fun findNDNCommonAncestor(
        first: Mutations<NucleotideSequence>,
        second: Mutations<NucleotideSequence>
    ): Mutations<NucleotideSequence> {
        val mutationsOfFirstByPositions = first.asSequence().groupBy { code -> Mutation.getPosition(code) }
        val mutationsBuilder = MutationsBuilder(ALPHABET)
        second.asSequence().forEach { mutationOfSecond ->
            val position = Mutation.getPosition(mutationOfSecond)
            val mutationsOfFirst = mutationsOfFirstByPositions[position] ?: emptyList()
            if (mutationsOfFirst.contains(mutationOfSecond)) {
                mutationsBuilder.append(mutationOfSecond)
            } else if (Mutation.isSubstitution(mutationOfSecond)) {
                val otherSubstitution = mutationsOfFirst.firstOrNull { code -> Mutation.isSubstitution(code) }
                if (otherSubstitution != null) {
                    val mutation = Mutation.createSubstitution(
                        position,
                        Mutation.getFrom(mutationOfSecond).toInt(),
                        combine(Mutation.getTo(mutationOfSecond), Mutation.getTo(otherSubstitution)).toInt()
                    )
                    mutationsBuilder.append(mutation)
                }
            }
        }
        return mutationsBuilder.createAndDestroy()
    }

    private fun combine(firstSymbol: Byte, secondSymbol: Byte): Byte = when {
        firstSymbol == secondSymbol -> firstSymbol
        ALPHABET.isWildcard(firstSymbol)
            && matchesStrictly(ALPHABET.codeToWildcard(firstSymbol), secondSymbol) -> secondSymbol
        ALPHABET.isWildcard(secondSymbol)
            && matchesStrictly(ALPHABET.codeToWildcard(secondSymbol), firstSymbol) -> firstSymbol
        else -> {
            val basicMask = (ALPHABET.codeToWildcard(firstSymbol).basicMask
                or ALPHABET.codeToWildcard(secondSymbol).basicMask)
            ALPHABET.maskToWildcard(basicMask).code
        }
    }

    private fun matchesStrictly(wildcard: Wildcard, secondSymbol: Byte): Boolean = when {
        !ALPHABET.isWildcard(secondSymbol) -> wildcard.matches(secondSymbol)
        else -> {
            val secondAsWildcard = ALPHABET.codeToWildcard(secondSymbol)
            wildcard.basicMask xor secondAsWildcard.basicMask and secondAsWildcard.basicMask == 0L
        }
    }
}

fun Map<Range, MutationsWithRange>.intersection(with: Map<Range, MutationsWithRange>): Map<Range, MutationsWithRange> =
    MutationsUtils.fold(this, with) { a, b, _ -> a.intersection(b) }

fun MutationsWithRange.intersection(with: MutationsWithRange): MutationsWithRange =
    copy(mutations = mutations.intersection(with.mutations))
