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
import com.milaboratory.mixcr.util.RangeInfo
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import java.util.*
import java.util.function.BiFunction
import kotlin.math.abs

internal object MutationsUtils {
    /**
     * Mutate and get result by range in original sequence
     */
    fun buildSequence(
        sequence1: NucleotideSequence,
        mutations: Mutations<NucleotideSequence>,
        rangeInfo: RangeInfo
    ): NucleotideSequence = mutations.mutate(sequence1).getRange(projectRange(mutations, rangeInfo))

    fun buildSequence(
        sequence1: NucleotideSequence,
        mutations: Mutations<NucleotideSequence>,
        rangeInfo: Range
    ): NucleotideSequence = mutations.mutate(sequence1).getRange(projectRange(mutations, rangeInfo))

    fun projectRange(mutations: Mutations<NucleotideSequence>, rangeInfo: RangeInfo): Range {
        //for including inclusions before position one must step left before conversion and step right after
        var from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(rangeInfo.range.lower))
        if (rangeInfo.isIncludeFirstInserts) {
            from -= mutations.asSequence()
                .count { mutation ->
                    Mutation.getPosition(mutation) == rangeInfo.range.lower && Mutation.isInsertion(mutation)
                }
        }
        val to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(rangeInfo.range.upper))
        return Range(from, to)
    }

    fun projectRange(mutations: Mutations<NucleotideSequence>, range: Range): Range {
        //for including inclusions before position one must step left before conversion and step right after
        val from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(range.lower)) -
            mutations.asSequence()
                .count { mutation -> Mutation.getPosition(mutation) == range.lower && Mutation.isInsertion(mutation) }
        val to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(range.upper))
        return Range(from, to)
    }

    fun mutationsBetween(first: MutationsDescription, second: MutationsDescription): MutationsDescription {
        return MutationsDescription(
            mutationsBetween(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
            first.VMutationsInCDR3WithoutNDN.differenceWith(second.VMutationsInCDR3WithoutNDN),
            first.knownNDN.differenceWith(second.knownNDN),
            first.JMutationsInCDR3WithoutNDN.differenceWith(second.JMutationsInCDR3WithoutNDN),
            mutationsBetween(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3)
        )
    }

    private fun mutationsBetween(
        firstMutations: List<MutationsWithRange>,
        secondMutations: List<MutationsWithRange>
    ): List<MutationsWithRange> {
        return fold(
            firstMutations,
            secondMutations
        ) { obj, comparison -> obj.differenceWith(comparison) }
    }

    fun intersection(
        base: MutationsWithRange,
        comparison: MutationsWithRange,
        intersection: RangeInfo
    ): MutationsWithRange {
        return MutationsWithRange(
            base.sequence1,
            intersection(
                base.mutations,
                comparison.mutations,
                intersection
            ),
            intersection
        )
    }

    fun intersection(from: MutationsWithRange, to: MutationsWithRange): MutationsWithRange {
        require(from.rangeInfo == to.rangeInfo)
        return MutationsWithRange(
            from.sequence1,
            intersection(
                from.mutations,
                to.mutations,
                from.rangeInfo
            ),
            from.rangeInfo
        )
    }

    fun intersection(from: List<MutationsWithRange>, to: List<MutationsWithRange>): List<MutationsWithRange> {
        return fold(from, to) { a, b ->
            intersection(
                a,
                b,
                a.rangeInfo.intersection(b.rangeInfo)!!
            )
        }
    }

    fun <T> fold(
        firstMutations: List<MutationsWithRange>,
        secondMutations: List<MutationsWithRange>,
        folder: BiFunction<MutationsWithRange, MutationsWithRange, T>
    ): List<T> {
        require(firstMutations.size == secondMutations.size)
        return (firstMutations.indices).map { folder.apply(firstMutations[it], secondMutations[it]) }
    }

    private fun positionIfNucleotideWasDeleted(position: Int): Int = when {
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

    fun intersection(
        first: Mutations<NucleotideSequence>,
        second: Mutations<NucleotideSequence>,
        rangeInfo: RangeInfo
    ): Mutations<NucleotideSequence> = simpleIntersection(first, second, rangeInfo.range)

    //TODO removals and inserts
    private fun simpleIntersection(
        first: Mutations<NucleotideSequence>,
        second: Mutations<NucleotideSequence>,
        range: Range
    ): Mutations<NucleotideSequence> {
        val mutationsOfFirstAsSet = first.asSequence().toSet()
        return second.asSequence()
            .filter { mutation ->
                val position = Mutation.getPosition(mutation)
                range.contains(position) && mutationsOfFirstAsSet.contains(mutation)
            }
            .asMutations(ALPHABET)
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

    fun combine(base: List<MutationsWithRange>, combineWith: List<MutationsWithRange>): List<MutationsWithRange> {
        return fold(
            base,
            combineWith
        ) { obj, next -> obj.combineWith(next) }
    }
}
