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
import com.milaboratory.core.sequence.Sequence
import com.milaboratory.core.sequence.Wildcard
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.mixcr.util.intersection
import io.repseq.core.GeneFeature
import java.util.*
import kotlin.math.abs

internal object MutationsUtils {
    /**
     * Mutate and get result by range in original sequence
     */
    fun <S : Sequence<S>> buildSequence(
        sequence1: S,
        mutations: Mutations<S>,
        range: Range
    ): S = mutations.mutate(sequence1).getRange(projectRange(mutations, range))

    fun <S : Sequence<S>> projectRange(mutations: Mutations<S>, range: Range): Range {
        //for including inclusions before position one must step left before conversion and step right after
        val from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(range.lower)) -
                mutations.asSequence()
                    .count { mutation -> Mutation.getPosition(mutation) == range.lower && Mutation.isInsertion(mutation) }
        val to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(range.upper))
        return Range(from, to)
    }

    fun difference(
        sequence1: NucleotideSequence,
        base: Mutations<NucleotideSequence>,
        comparison: Mutations<NucleotideSequence>,
        range: Range = Range(0, sequence1.size())
    ): CompositeMutations = CompositeMutations(
        sequence1,
        base,
        range,
        base.invert().combineWith(comparison)
    )

    inline fun <R, V1, V2> zip(
        first: Map<GeneFeature, V1>,
        second: Map<GeneFeature, V2>,
        function: (V1, V2, GeneFeature) -> R
    ): Map<GeneFeature, R> {
        require(first.keys == second.keys)
        return first.mapValues { (geneFeature, value) -> function(value, second[geneFeature]!!, geneFeature) }
    }

    inline fun <R, V1, V2> fold(
        first: Map<GeneFeature, V1>,
        second: Map<GeneFeature, V2>,
        function: (V1, V2, GeneFeature) -> R,
    ): Collection<R> {
        require(first.keys == second.keys)
        return first.map { (geneFeature, value) -> function(value, second[geneFeature]!!, geneFeature) }
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

        //TODO move to main library
        //TODO add comments
        //TODO remove excludeSet from milib
        for (wc1 in alphabet.allWildcards) {
            for (wc2 in alphabet.allWildcards) {
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
        }
        return matrix
    }

    /**
     * If parent was replaced we may recalculate NDN of the child.
     *
     * If there is a letter/wildcard in the child include the parent letter, replace child letter with parent letter.
     * In this case there will be no mutation from parent to child by choosing one of already possible variances of child.
     *
     * If a child letter is a wildcard, but it's not included in parent, then we need to add possibility that there
     * is no mutation between parent and child, so we replace child letter with union.
     *
     * If a child letter and the parent letter are not equal and are not wildcards, then assume that there is a mutation.
     *
     */
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
            ALPHABET.codeToWildcard(childSymbol).matchesStrictly(parentSymbol) -> parentSymbol
            else -> ALPHABET.codeToWildcard(parentSymbol).union(ALPHABET.codeToWildcard(childSymbol))
        }
        else -> childSymbol
    }

    /**
     * Calculate post possible NDN of common ancestor. If there is uncertainty - left wildcard.
     * Input sequences may contain wildcards. This wildcards may be resolved to letter or narrower wildcard.
     *
     * If in position are different letters or not overlapping wildcards than resolve to wider wildcard.
     *
     * If in position are letter and wildcard containing this letter than resolve to this letter.
     * It's because most likely mutation occurred only in one branch.
     *
     * If in position are overlapping wildcards than resolve to the overlapping wildcard.
     * It's because most likely mutation occurred only in one branch.
     */
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
            && ALPHABET.codeToWildcard(firstSymbol).matchesStrictly(secondSymbol) -> secondSymbol
        ALPHABET.isWildcard(secondSymbol)
            && ALPHABET.codeToWildcard(secondSymbol).matchesStrictly(firstSymbol) -> firstSymbol
        else -> ALPHABET.codeToWildcard(firstSymbol).union(ALPHABET.codeToWildcard(secondSymbol))
    }

    private fun Wildcard.union(second: Wildcard): Byte =
        ALPHABET.maskToWildcard(basicMask or second.basicMask).code

    private fun Wildcard.matchesStrictly(secondSymbol: Byte): Boolean = when {
        !ALPHABET.isWildcard(secondSymbol) -> matches(secondSymbol)
        else -> {
            val secondAsWildcard = ALPHABET.codeToWildcard(secondSymbol)
            basicMask xor secondAsWildcard.basicMask and secondAsWildcard.basicMask == 0L
        }
    }
}

fun Map<GeneFeature, CompositeMutations>.intersection(with: Map<GeneFeature, CompositeMutations>): Map<GeneFeature, CompositeMutations> =
    MutationsUtils.zip(this, with) { a, b, _ -> a.intersection(b) }

fun CompositeMutations.intersection(with: CompositeMutations): CompositeMutations =
    copy(mutationsFromParentToThis = mutationsFromParentToThis.intersection(with.mutationsFromParentToThis))
