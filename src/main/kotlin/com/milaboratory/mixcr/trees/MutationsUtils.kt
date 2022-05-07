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
import com.milaboratory.core.sequence.Wildcard
import com.milaboratory.mixcr.util.RangeInfo
import java.util.*
import java.util.function.BiFunction
import java.util.stream.Collectors
import java.util.stream.IntStream
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

    fun projectRange(mutations: Mutations<NucleotideSequence>, rangeInfo: RangeInfo?): Range {
        //for including inclusions before position one must step left before conversion and step right after
        var from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(rangeInfo!!.range.lower))
        if (rangeInfo.isIncludeFirstInserts) {
            from -= IntStream.of(*mutations.rawMutations)
                .filter { mutation: Int ->
                    Mutation.getPosition(mutation) == rangeInfo.range.lower && Mutation.isInsertion(
                        mutation
                    )
                }
                .count().toInt()
        }
        val to = positionIfNucleotideWasDeleted(
            mutations.convertToSeq2Position(
                rangeInfo.range.upper
            )
        )
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
        return IntStream.range(0, firstMutations.size)
            .mapToObj { folder.apply(firstMutations[0], secondMutations[0]) }
            .collect(Collectors.toList())
    }

    private fun positionIfNucleotideWasDeleted(position: Int): Int = when {
        position < -1 -> abs(position + 1)
        (position == -1) -> 0
        else -> position
    }

    fun NDNScoring(): AlignmentScoring<NucleotideSequence> = AffineGapAlignmentScoring(
        NucleotideSequence.ALPHABET,
        calculateSubstitutionMatrix(5, -4, 4, NucleotideSequence.ALPHABET),
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
    ): Mutations<NucleotideSequence> = simpleIntersection(
        first,
        second,
        rangeInfo.range
    )

    //TODO removals and inserts
    private fun simpleIntersection(
        first: Mutations<NucleotideSequence>,
        second: Mutations<NucleotideSequence>,
        range: Range
    ): Mutations<NucleotideSequence> {
        val mutationsOfFirstAsSet = Arrays.stream(first.rawMutations).boxed().collect(Collectors.toSet())
        val mutationsBuilder = MutationsBuilder(NucleotideSequence.ALPHABET)
        for (i in 0 until second.size()) {
            val mutation = second.getMutation(i)
            val position = Mutation.getPosition(mutation)
            if (range.contains(position)) {
                if (mutationsOfFirstAsSet.contains(mutation)) {
                    mutationsBuilder.append(mutation)
                }
            }
        }
        return mutationsBuilder.createAndDestroy()
    }

    //TODO removals and inserts
    fun concreteNDNChild(
        parent: Mutations<NucleotideSequence>,
        child: Mutations<NucleotideSequence>
    ): Mutations<NucleotideSequence> {
        val mutationsOfParentByPositions = Arrays.stream(
            parent.rawMutations
        )
            .boxed()
            .collect(
                Collectors.groupingBy(
                    { code -> Mutation.getPosition(code) }, Collectors.toSet()
                )
            )
        val mutationsBuilder = MutationsBuilder(NucleotideSequence.ALPHABET)
        for (i in 0 until child.size()) {
            val mutationOfChild = child.getMutation(i)
            if (Mutation.isInDel(mutationOfChild)) {
                mutationsBuilder.append(mutationOfChild)
            } else {
                val position = Mutation.getPosition(mutationOfChild)
                val mutationsOfParent = (mutationsOfParentByPositions[position] ?: emptySet()).stream()
                    .filter { code -> Mutation.isSubstitution(code) }
                    .findFirst()
                if (!mutationsOfParent.isPresent) {
                    mutationsBuilder.append(mutationOfChild)
                } else {
                    val from = Mutation.getFrom(mutationOfChild)
                    val to = concreteChild(Mutation.getTo(mutationsOfParent.get()), Mutation.getTo(mutationOfChild))
                    if (from != to) {
                        mutationsBuilder.append(Mutation.createSubstitution(position, from.toInt(), to.toInt()))
                    }
                }
            }
        }
        return mutationsBuilder.createAndDestroy()
    }

    private fun concreteChild(parentSymbol: Byte, childSymbol: Byte): Byte {
        return if (parentSymbol == childSymbol) {
            childSymbol
        } else if (NucleotideSequence.ALPHABET.isWildcard(childSymbol)) {
            if (matchesStrictly(NucleotideSequence.ALPHABET.codeToWildcard(childSymbol), parentSymbol)) {
                parentSymbol
            } else {
                val basicMask = (NucleotideSequence.ALPHABET.codeToWildcard(parentSymbol).basicMask
                    or NucleotideSequence.ALPHABET.codeToWildcard(childSymbol).basicMask)
                NucleotideSequence.ALPHABET.maskToWildcard(basicMask).code
            }
        } else {
            childSymbol
        }
    }

    //TODO removals and inserts
    fun findNDNCommonAncestor(
        first: Mutations<NucleotideSequence>,
        second: Mutations<NucleotideSequence>
    ): Mutations<NucleotideSequence> {
        val mutationsOfFirstByPositions = Arrays.stream(
            first.rawMutations
        )
            .boxed()
            .collect(
                Collectors.groupingBy(
                    { code -> Mutation.getPosition(code) }, Collectors.toSet()
                )
            )
        val mutationsBuilder = MutationsBuilder(NucleotideSequence.ALPHABET)
        for (i in 0 until second.size()) {
            val mutationOfSecond = second.getMutation(i)
            val position = Mutation.getPosition(mutationOfSecond)
            val mutationsOfFirst = mutationsOfFirstByPositions[position] ?: emptySet()
            if (mutationsOfFirst.contains(mutationOfSecond)) {
                mutationsBuilder.append(mutationOfSecond)
            } else if (Mutation.isSubstitution(mutationOfSecond)) {
                mutationsOfFirst.stream()
                    .filter { code -> Mutation.isSubstitution(code) }
                    .findFirst()
                    .map { otherSubstitution ->
                        Mutation.createSubstitution(
                            position,
                            Mutation.getFrom(mutationOfSecond).toInt(),
                            combine(Mutation.getTo(mutationOfSecond), Mutation.getTo(otherSubstitution)).toInt()
                        )
                    }
                    .ifPresent { mutation: Int? -> mutationsBuilder.append(mutation!!) }
            }
        }
        return mutationsBuilder.createAndDestroy()
    }

    private fun combine(firstSymbol: Byte, secondSymbol: Byte): Byte = when {
        firstSymbol == secondSymbol -> firstSymbol
        NucleotideSequence.ALPHABET.isWildcard(firstSymbol)
            && matchesStrictly(NucleotideSequence.ALPHABET.codeToWildcard(firstSymbol), secondSymbol) -> secondSymbol
        NucleotideSequence.ALPHABET.isWildcard(secondSymbol)
            && matchesStrictly(NucleotideSequence.ALPHABET.codeToWildcard(secondSymbol), firstSymbol) -> firstSymbol
        else -> {
            val basicMask = (NucleotideSequence.ALPHABET.codeToWildcard(firstSymbol).basicMask
                or NucleotideSequence.ALPHABET.codeToWildcard(secondSymbol).basicMask)
            NucleotideSequence.ALPHABET.maskToWildcard(basicMask).code
        }
    }

    private fun matchesStrictly(wildcard: Wildcard, secondSymbol: Byte): Boolean = when {
        !NucleotideSequence.ALPHABET.isWildcard(secondSymbol) -> wildcard.matches(secondSymbol)
        else -> {
            val secondAsWildcard = NucleotideSequence.ALPHABET.codeToWildcard(secondSymbol)
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
