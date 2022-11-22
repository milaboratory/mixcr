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
import com.milaboratory.core.sequence.Sequence
import com.milaboratory.util.BitArray
import com.milaboratory.util.IntArrayList
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MultiAlignmentHelper private constructor(
    val subject: LineWithPositions,
    private val queries: Array<LineWithPositions>,
    val match: Array<BitArray>
) {
    // subject / queries nomenclature seems to be swapped here...
    // subject here corresponds to sequence2 from alignments (so, the query sequence)
    // queries here corresponds to sequence1 from alignments (so, the reference sequence)
    private val annotations = mutableListOf<AnnotationLine>()

    fun getQuery(idx: Int): String = queries[idx].content

    val actualPositionWidth: Int
        get() = (queries.map { it.firstPosition.toString() } + subject.firstPosition.toString())
            .maxOf { it.length }

    fun addAnnotation(annotationLine: AnnotationLine): MultiAlignmentHelper {
        require(annotationLine.content.length == size())
        annotations += annotationLine
        return this
    }

    fun subjectToAlignmentPosition(subjectPosition: Int): Int {
        for (i in subject.positions.indices) if (subject.positions[i] == subjectPosition) return i
        return -1
    }

    fun getAbsSubjectPositionAt(position: Int): Int = aabs(subject.positions[position])

    fun getAbsQueryPositionAt(index: Int, position: Int): Int = aabs(queries[index].positions[position])

    fun getAnnotationString(i: Int): String = annotations[i].content

    fun size(): Int = subject.content.length

    fun getRange(from: Int, to: Int): MultiAlignmentHelper {
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

        val cQueries: Array<LineWithPositions> = Array(queriesCount) {
            val origin = queries[indexMapping[it]]
            origin.subRange(from, to)
        }
        val cMatch: Array<BitArray> = Array(queriesCount) {
            match[indexMapping[it]].getRange(from, to)
        }
        val result = MultiAlignmentHelper(
            subject.subRange(from, to),
            cQueries,
            cMatch
        )
        result.annotations += annotations.map {
            it.subRange(from, to)
        }
        return result
    }

    fun split(length: Int): Array<MultiAlignmentHelper> = Array((size() + length - 1) / length) { i ->
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

    override fun toString(): String = formatLines()

    fun formatLines(minimalPositionWidth: Int = 0): String {
        val aCount = queries.size
        val asSize = annotations.size
        val lines: Array<String> = Array(aCount + 1 + asSize) { "" }
        lines[asSize] = "" + subject.firstPosition
        for (i in 0 until aCount)
            lines[i + 1 + asSize] = "" + queries[i].firstPosition
        val width = fixedWidthL(lines, minimalPositionWidth)
        for (i in 0 until asSize)
            lines[i] = annotations[i].leftTitle + spaces(width + 1)
        lines[asSize] = subject.leftTitle + " " + lines[asSize]
        for (i in 0 until aCount)
            lines[i + 1 + asSize] = queries[i].leftTitle + " " + lines[i + 1 + asSize]
        fixedWidthL(lines)

        for (i in 0 until asSize)
            lines[i] += " " + annotations[i].content
        lines[asSize] += " ${subject.content} ${subject.lastPosition}"
        for (i in 0 until aCount)
            lines[i + 1 + asSize] += " ${queries[i].content} ${queries[i].lastPosition}"

        fixedWidthR(lines)
        lines[asSize] += " ${subject.rightTitle}"
        for (i in 0 until aCount)
            if (queries[i].rightTitle != null) lines[i + 1 + asSize] += " " + queries[i].rightTitle
        val result = StringBuilder()
        for (i in lines.indices) {
            if (i != 0) result.append("\n")
            result.append(lines[i])
        }
        return result.toString()
    }

    companion object {
        private fun aabs(pos: Int): Int {
            if (pos >= 0) return pos
            return if (pos == -1) -1 else -2 - pos
        }

        private fun fixedWidthL(strings: Array<String>, minWidth: Int = 0): Int {
            var length = 0
            for (string in strings) length = max(length, string.length)
            length = max(length, minWidth)
            for (i in strings.indices) strings[i] = spaces(length - strings[i].length) + strings[i]
            return length
        }

        private fun fixedWidthR(strings: Array<String>, minWidth: Int = 0): Int {
            var length = 0
            for (string in strings) length = max(length, string.length)
            length = max(length, minWidth)
            for (i in strings.indices) strings[i] = strings[i] + spaces(length - strings[i].length)
            return length
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
            settings: Settings, subjectRange: Range,
            leftTitle: String,
            rightTitle: String,
            vararg alignments: Input<S>
        ): MultiAlignmentHelper =
            build(settings, subjectRange, leftTitle, rightTitle, alignments[0].alignment.sequence1, *alignments)

        @JvmStatic
        @SafeVarargs
        fun <S : Sequence<S>> build(
            settings: Settings,
            subjectRange: Range,
            leftTitle: String,
            rightTitle: String,
            subject: S,
            vararg inputs: Input<S>
        ): MultiAlignmentHelper {
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
            val queryStringsArray: Array<LineWithPositions> = Array(aCount) {
                LineWithPositions(
                    leftTitle = inputs[it].leftTitle,
                    content = queryStrings[it].toString(),
                    rightTitle = inputs[it].rightTitle,
                    positions = queryPositions[it].toArray()
                )
            }
            return MultiAlignmentHelper(
                LineWithPositions(
                    leftTitle = leftTitle,
                    content = subjectString.toString(),
                    rightTitle = rightTitle,
                    positions = subjectPositions.toArray()
                ),
                queryStringsArray,
                matchesBAs
            )
        }

        private fun spaces(n: Int): String {
            val c = CharArray(n)
            Arrays.fill(c, ' ')
            return String(c)
        }
    }

    class AnnotationLine(
        val leftTitle: String,
        val content: String
    ) {
        fun subRange(from: Int, to: Int) = AnnotationLine(
            leftTitle = leftTitle,
            content = content.substring(from, to)
        )
    }

    class LineWithPositions(
        val leftTitle: String,
        val content: String,
        val rightTitle: String?,
        val positions: IntArray
    ) {
        fun subRange(from: Int, to: Int) = LineWithPositions(
            leftTitle = leftTitle,
            content = content.substring(from, to),
            rightTitle = rightTitle,
            positions = positions.copyOfRange(from, to)
        )

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

    data class Input<S : Sequence<S>>(
        val leftTitle: String,
        val alignment: Alignment<S>,
        val rightTitle: String?
    )
}


// may be used for calculation fo minimalPositionWidth for formatLines
@Suppress("unused")
val Array<MultiAlignmentHelper>.maxPositionWidth: Int
    get() = maxOf { it.actualPositionWidth }

