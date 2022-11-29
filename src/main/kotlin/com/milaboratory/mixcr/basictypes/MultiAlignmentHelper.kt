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
package com.milaboratory.mixcr.basictypes

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.MutationType
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Sequence
import com.milaboratory.core.sequence.SequenceQuality
import com.milaboratory.util.BitArray
import com.milaboratory.util.IntArrayList
import io.repseq.core.SequencePartitioning
import kotlin.math.min

class MultiAlignmentHelper<S : Sequence<S>> private constructor(
    // subject / queries nomenclature seems to be swapped here...
    // subject here corresponds to sequence2 from alignments (so, the query sequence)
    // queries here corresponds to sequence1 from alignments (so, the reference sequence)
    val subject: SubjectLine<S>,
    val queries: Array<QueryLine>,
    val match: Array<BitArray>,
    val metaInfo: List<MetaInfoInput<S>>
) {


    fun getQuery(idx: Int): String = queries[idx].content

    val actualPositionWidth: Int
        get() = (queries.map { it.firstPosition.toString() } + subject.firstPosition.toString())
            .maxOf { it.length }

    fun subjectToAlignmentPosition(subjectPosition: Int): Int {
        for (i in subject.positions.indices) if (subject.positions[i] == subjectPosition) return i
        return -1
    }

    fun getAbsSubjectPositionAt(position: Int): Int = aabs(subject.positions[position])

    fun getAbsQueryPositionAt(index: Int, position: Int): Int = aabs(queries[index].positions[position])

    fun size(): Int = subject.content.length

    fun getRange(from: Int, to: Int): MultiAlignmentHelper<S> {
        val queriesToExclude = BooleanArray(queries.size)
        var queriesCount = 0
        for (i in queries.indices) {
            var exclude = true
            for (j in from until to) if (queries[i].positions[j] != -1) {
                exclude = false
                break
            }
            queriesToExclude[i] = exclude
            if (!exclude) queriesCount++
        }
        val indexMapping = IntArray(queriesCount)
        var j = 0
        for (i in queries.indices) {
            if (queriesToExclude[i]) continue
            indexMapping[j] = i
            j++
        }

        val cQueries: Array<QueryLine> = Array(queriesCount) {
            val origin = queries[indexMapping[it]]
            origin.subRange(from, to)
        }
        val cMatch: Array<BitArray> = Array(queriesCount) {
            match[indexMapping[it]].getRange(from, to)
        }
        return MultiAlignmentHelper(
            subject.subRange(from, to),
            cQueries,
            cMatch,
            metaInfo
        )
    }

    fun split(length: Int): Array<MultiAlignmentHelper<S>> = Array((size() + length - 1) / length) { i ->
        val pointer = i * length
        val l = min(length, size() - pointer)
        getRange(pointer, pointer + l)
    }

    class Settings(
        val markMatchWithSpecialLetter: Boolean,
        val lowerCaseMatch: Boolean,
        val lowerCaseMismatch: Boolean,
        val specialMatchChar: Char,
        val outOfRangeChar: Char
    )

    override fun toString(): String = format()

    @JvmOverloads
    fun format(linesFormatter: MultiAlignmentFormatter.LinesFormatter = MultiAlignmentFormatter.LinesFormatter()): String =
        linesFormatter.formatLines(this)

    companion object {
        private fun aabs(pos: Int): Int {
            if (pos >= 0) return pos
            return if (pos == -1) -1 else -2 - pos
        }

        @JvmField
        val DEFAULT_SETTINGS = Settings(
            markMatchWithSpecialLetter = false,
            lowerCaseMatch = true,
            lowerCaseMismatch = false,
            specialMatchChar = ' ',
            outOfRangeChar = ' '
        )
        val DOT_MATCH_SETTINGS = Settings(
            markMatchWithSpecialLetter = true,
            lowerCaseMatch = true,
            lowerCaseMismatch = false,
            specialMatchChar = '.',
            outOfRangeChar = ' '
        )

        @JvmStatic
        @SafeVarargs
        fun <S : Sequence<S>> build(
            settings: Settings,
            subjectRange: Range,
            name: String,
            subject: S,
            inputs: List<Input<S>>,
            metaInfo: List<MetaInfoInput<S>>
        ): MultiAlignmentHelper<S> {
            for (input in inputs) require(input.alignment.sequence1 == subject)
            var subjectPointer = subjectRange.from
            val subjectPointerTo = subjectRange.to
            val aCount = inputs.size
            val queryPointers = IntArray(aCount) { inputs[it].alignment.sequence2Range.from }
            val mutationPointers = IntArray(aCount)
            val mutations: Array<Mutations<S>> = Array(aCount) { inputs[it].alignment.absoluteMutations }
            val matches: Array<MutableList<Boolean>> = Array(aCount) { ArrayList() }
            val subjectPositions = IntArrayList()
            val queryPositions: Array<IntArrayList> = Array(aCount) { IntArrayList() }
            val subjectString = StringBuilder()
            val queryStrings: Array<StringBuilder> = Array(aCount) { StringBuilder() }
            val processed = BitArray(aCount)
            while (true) {
                // Checking continue condition
                var doContinue = subjectPointer < subjectPointerTo
                for (i in 0 until aCount) doContinue = doContinue or (mutationPointers[i] < mutations[i].size())
                if (!doContinue) break
                processed.clearAll()

                // Processing out of range sequences
                for (i in 0 until aCount) {
                    if (!inputs[i].alignment.sequence1Range.contains(subjectPointer)
                        && !(inputs[i].alignment.sequence1Range.containsBoundary(subjectPointer) &&
                                mutationPointers[i] != mutations[i].size())
                    ) {
                        queryStrings[i].append(settings.outOfRangeChar)
                        queryPositions[i].add(-1)
                        matches[i].add(false)
                        processed.set(i)
                    }
                }

                // Checking for insertions
                var insertion = false
                for (i in 0 until aCount) {
                    if (mutationPointers[i] < mutations[i].size() &&
                        mutations[i].getTypeByIndex(mutationPointers[i]) == MutationType.Insertion &&
                        mutations[i].getPositionByIndex(mutationPointers[i]) == subjectPointer
                    ) {
                        insertion = true
                        queryStrings[i].append(mutations[i].getToAsSymbolByIndex(mutationPointers[i]))
                        queryPositions[i].add(queryPointers[i]++)
                        matches[i].add(false)
                        mutationPointers[i]++
                        assert(!processed[i])
                        processed.set(i)
                    }
                }
                if (insertion) { // In case on insertion in query sequence
                    subjectString.append('-')
                    subjectPositions.add(-2 - subjectPointer)
                    for (i in 0 until aCount) {
                        if (!processed[i]) {
                            queryStrings[i].append('-')
                            queryPositions[i].add(-2 - queryPointers[i])
                            matches[i].add(false)
                        }
                    }
                } else { // In other cases
                    val subjectSymbol = subject.symbolAt(subjectPointer)
                    subjectString.append(subjectSymbol)
                    subjectPositions.add(subjectPointer)
                    for (i in 0 until aCount) {
                        if (processed[i]) continue
                        val cMutations = mutations[i]
                        val cMutationPointer = mutationPointers[i]
                        var mutated = false
                        if (cMutationPointer < cMutations.size()) {
                            val mutPosition = cMutations.getPositionByIndex(cMutationPointer)
                            assert(mutPosition >= subjectPointer)
                            mutated = mutPosition == subjectPointer
                        }
                        if (mutated) {
                            when (cMutations.getRawTypeByIndex(cMutationPointer)) {
                                Mutation.RAW_MUTATION_TYPE_SUBSTITUTION -> {
                                    val symbol = cMutations.getToAsSymbolByIndex(cMutationPointer)
                                    queryStrings[i].append(if (settings.lowerCaseMismatch) symbol.lowercaseChar() else symbol)
                                    queryPositions[i].add(queryPointers[i]++)
                                    matches[i].add(false)
                                }
                                Mutation.RAW_MUTATION_TYPE_DELETION -> {
                                    queryStrings[i].append('-')
                                    queryPositions[i].add(-2 - queryPointers[i])
                                    matches[i].add(false)
                                }
                                else -> assert(false)
                            }
                            mutationPointers[i]++
                        } else {
                            queryStrings[i].append(
                                when {
                                    settings.markMatchWithSpecialLetter -> settings.specialMatchChar
                                    settings.lowerCaseMatch -> subjectSymbol.lowercaseChar()
                                    else -> subjectSymbol
                                }
                            )
                            queryPositions[i].add(queryPointers[i]++)
                            matches[i].add(true)
                        }
                    }
                    subjectPointer++
                }
            }
            val matchesBAs: Array<BitArray> = Array(aCount) {
                BitArray(matches[it])
            }
            val queryStringsArray: Array<QueryLine> = Array(aCount) { i ->
                when (val input = inputs[i]) {
                    is AlignmentInput -> AlignmentLine(
                        geneName = input.geneName,
                        content = queryStrings[i].toString(),
                        positions = queryPositions[i].toArray(),
                        alignmentScore = input.alignmentScore,
                        hitScore = input.hitScore
                    )
                    is ReadInput -> ReadLine(
                        index = input.index,
                        content = queryStrings[i].toString(),
                        positions = queryPositions[i].toArray(),
                    )
                }
            }
            return MultiAlignmentHelper(
                SubjectLine(
                    name = name,
                    source = subject,
                    content = subjectString.toString(),
                    positions = subjectPositions.toArray()
                ),
                queryStringsArray,
                matchesBAs,
                metaInfo
            )
        }
    }

    sealed interface MetaInfoInput<S : Sequence<S>>

    class ReferencePointsInput<S : Sequence<S>>(
        val partitioning: SequencePartitioning
    ) : MetaInfoInput<S>

    class AminoAcidInput(
        val partitioning: SequencePartitioning
    ) : MetaInfoInput<NucleotideSequence>

    class QualityInput(
        val quality: SequenceQuality
    ) : MetaInfoInput<NucleotideSequence>

    sealed interface AnnotationLine {
        val content: String
        fun subRange(from: Int, to: Int): AnnotationLine
    }

    data class QualityLine(
        override val content: String
    ) : AnnotationLine {
        override fun subRange(from: Int, to: Int) = copy(
            content = content.substring(from, to)
        )
    }

    data class AminoAcidsLine(
        override val content: String
    ) : AnnotationLine {
        override fun subRange(from: Int, to: Int) = copy(
            content = content.substring(from, to)
        )
    }

    data class ReferencePointsLine(
        override val content: String
    ) : AnnotationLine {
        override fun subRange(from: Int, to: Int) = copy(
            content = content.substring(from, to)
        )
    }

    sealed interface LineWithPositions {
        val content: String
        val positions: IntArray
        fun subRange(from: Int, to: Int): LineWithPositions

        val firstPosition: Int
            get() {
                for (pos in positions) if (pos >= 0) return pos
                for (pos in positions) if (pos < -1) return -2 - pos
                return -1
            }

        val lastPosition: Int
            get() {
                for (i in positions.indices.reversed()) if (positions[i] >= 0) return positions[i]
                for (i in positions.indices.reversed()) if (positions[i] < -1) return -2 - positions[i]
                return -1
            }
    }

    sealed interface QueryLine : LineWithPositions {
        override fun subRange(from: Int, to: Int): QueryLine
    }

    class SubjectLine<S : Sequence<S>>(
        val name: String,
        val source: S,
        override val content: String,
        override val positions: IntArray
    ) : LineWithPositions {
        override fun subRange(from: Int, to: Int): SubjectLine<S> = SubjectLine(
            name = name,
            source = source,
            content = content.substring(from, to),
            positions = positions.copyOfRange(from, to)
        )
    }

    class AlignmentLine(
        val geneName: String,
        override val content: String,
        override val positions: IntArray,
        val alignmentScore: Int,
        val hitScore: Int
    ) : QueryLine {
        override fun subRange(from: Int, to: Int) = AlignmentLine(
            geneName = geneName,
            content = content.substring(from, to),
            positions = positions.copyOfRange(from, to),
            alignmentScore = alignmentScore,
            hitScore = hitScore
        )
    }


    class ReadLine(
        val index: String,
        override val content: String,
        override val positions: IntArray
    ) : QueryLine {
        override fun subRange(from: Int, to: Int) = ReadLine(
            index = index,
            content = content.substring(from, to),
            positions = positions.copyOfRange(from, to)
        )
    }

    sealed interface Input<S : Sequence<S>> {
        val alignment: Alignment<S>
    }

    class AlignmentInput<S : Sequence<S>>(
        val geneName: String,
        override val alignment: Alignment<S>,
        val alignmentScore: Int,
        val hitScore: Int
    ) : Input<S>

    class ReadInput<S : Sequence<S>>(
        val index: String,
        override val alignment: Alignment<S>
    ) : Input<S>
}


// may be used for calculation fo minimalPositionWidth for formatLines
@Suppress("unused")
val Array<MultiAlignmentHelper<*>>.maxPositionWidth: Int
    get() = maxOf { it.actualPositionWidth }

