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
import com.milaboratory.core.sequence.SequenceQuality
import com.milaboratory.util.BitArray
import com.milaboratory.util.IntArrayList
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MultiAlignmentHelper private constructor(
    val subject: String,
    private val queries: Array<String>,
    private val subjectPositions: IntArray,
    private val queryPositions: Array<IntArray>,
    val match: Array<BitArray>,
    var subjectLeftTitle: String? = "",
    private val queryLeftTitles: Array<String?> = arrayOfNulls(queries.size),
    var subjectRightTitle: String = "",
    private val queryRightTitles: Array<String?> = arrayOfNulls(queries.size)
) {
    // subject / queries nomenclature seems to be swapped here...
    // subject here corresponds to sequence2 from alignments (so, the query sequence)
    // queries here corresponds to sequence1 from alignments (so, the reference sequence)
    private var minimalPositionWidth = 0
    private val annotationStrings = mutableListOf<String>()
    private val annotationStringTitles = mutableListOf<String>()

    fun getQuery(idx: Int): String = queries[idx]

    val actualPositionWidth: Int
        get() {
            var ret = ("" + subjectFrom).length
            for (i in queries.indices) ret = max(ret, ("" + getQueryFrom(i)).length)
            return ret
        }

    fun addSubjectQuality(title: String, quality: SequenceQuality): MultiAlignmentHelper {
        val chars = CharArray(size())
        for (i in 0 until size()) chars[i] = when {
            subjectPositions[i] < 0 -> ' '
            else -> simplifiedQuality(quality.value(subjectPositions[i]).toInt())
        }
        addAnnotationString(title, String(chars))
        return this
    }

    fun addAnnotationString(title: String, string: String): MultiAlignmentHelper {
        require(string.length == size())
        annotationStrings.add(string)
        annotationStringTitles.add(title)
        return this
    }

    fun setQueryLeftTitle(id: Int, queryLeftTitle: String?): MultiAlignmentHelper {
        queryLeftTitles[id] = queryLeftTitle
        return this
    }

    fun setQueryRightTitle(id: Int, queryRightTitle: String?): MultiAlignmentHelper {
        queryRightTitles[id] = queryRightTitle
        return this
    }

    fun subjectToAlignmentPosition(subjectPosition: Int): Int {
        for (i in subjectPositions.indices) if (subjectPositions[i] == subjectPosition) return i
        return -1
    }

    fun getAbsSubjectPositionAt(position: Int): Int = aabs(subjectPositions[position])

    fun getAbsQueryPositionAt(index: Int, position: Int): Int = aabs(queryPositions[index][position])

    private val subjectFrom: Int
        get() = getFirstPosition(subjectPositions)
    private val subjectTo: Int
        get() = getLastPosition(subjectPositions)

    private fun getQueryFrom(index: Int): Int = getFirstPosition(queryPositions[index])

    private fun getQueryTo(index: Int): Int = getLastPosition(queryPositions[index])

    fun getAnnotationString(i: Int): String = annotationStrings[i]

    fun size(): Int = subject.length

    fun getRange(from: Int, to: Int): MultiAlignmentHelper {
        val queriesToExclude = BooleanArray(queries.size)
        var queriesCount = 0
        for (i in queries.indices) {
            var exclude = true
            for (j in from until to) if (queryPositions[i][j] != -1) {
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

        val cQueries: Array<String> = Array(queriesCount) {
            queries[indexMapping[it]].substring(from, to)
        }
        val cQueryPositions: Array<IntArray> = Array(queriesCount) {
            queryPositions[indexMapping[it]].copyOfRange(from, to)
        }
        val cMatch: Array<BitArray> = Array(queriesCount) {
            match[indexMapping[it]].getRange(from, to)
        }
        val cQueryLeftTitles: Array<String?> = Array(queriesCount) {
            queryLeftTitles[indexMapping[it]]
        }
        val cQueryRightTitles: Array<String?> = Array(queriesCount) {
            queryRightTitles[indexMapping[it]]
        }
        val result = MultiAlignmentHelper(
            subject.substring(from, to), cQueries,
            subjectPositions.copyOfRange(from, to), cQueryPositions, cMatch,
            subjectLeftTitle, cQueryLeftTitles, subjectRightTitle, cQueryRightTitles
        )
        for (i in annotationStrings.indices)
            result.addAnnotationString(
                annotationStringTitles[i],
                annotationStrings[i].substring(from, to)
            )
        return result
    }

    @JvmOverloads
    fun split(length: Int, eqPositionWidth: Boolean = false): Array<MultiAlignmentHelper> {
        val ret: Array<MultiAlignmentHelper> = Array((size() + length - 1) / length) { i ->
            val pointer = i * length
            val l = min(length, size() - pointer)
            getRange(pointer, pointer + l)
        }
        if (eqPositionWidth) alignPositions(ret)
        return ret
    }

    class Settings(
        val markMatchWithSpecialLetter: Boolean,
        val lowerCaseMatch: Boolean,
        val lowerCaseMismatch: Boolean,
        val specialMatchChar: Char,
        val outOfRangeChar: Char
    )

    override fun toString(): String {
        val aCount = queries.size
        val asSize = annotationStringTitles.size
        val lines: Array<String> = Array(aCount + 1 + asSize) { "" }
        lines[asSize] = "" + subjectFrom
        for (i in 0 until aCount)
            lines[i + 1 + asSize] = "" + getQueryFrom(i)
        val width = fixedWidthL(lines, minimalPositionWidth)
        for (i in 0 until asSize)
            lines[i] = annotationStringTitles[i] + spaces(width + 1)
        lines[asSize] = (subjectLeftTitle ?: "") + " " + lines[asSize]
        for (i in 0 until aCount)
            lines[i + 1 + asSize] = (queryLeftTitles[i] ?: "") + " " + lines[i + 1 + asSize]
        fixedWidthL(lines)

        for (i in 0 until asSize)
            lines[i] += " " + annotationStrings[i]
        lines[asSize] += " $subject $subjectTo"
        for (i in 0 until aCount)
            lines[i + 1 + asSize] += " ${queries[i]} ${getQueryTo(i)}"

        fixedWidthR(lines)
        lines[asSize] += " $subjectRightTitle"
        for (i in 0 until aCount)
            if (queryRightTitles[i] != null) lines[i + 1 + asSize] += " " + queryRightTitles[i]
        val result = StringBuilder()
        for (i in lines.indices) {
            if (i != 0) result.append("\n")
            result.append(lines[i])
        }
        return result.toString()
    }

    companion object {
        private fun simplifiedQuality(value: Int): Char {
            var result = value
            result /= 5
            if (result > 9) result = 9
            return result.toString()[0]
        }

        private fun aabs(pos: Int): Int {
            if (pos >= 0) return pos
            return if (pos == -1) -1 else -2 - pos
        }

        private fun getFirstPosition(array: IntArray): Int {
            for (pos in array) if (pos >= 0) return pos
            for (pos in array) if (pos < -1) return -2 - pos
            return -1
        }

        private fun getLastPosition(array: IntArray): Int {
            for (i in array.indices.reversed()) if (array[i] >= 0) return array[i]
            for (i in array.indices.reversed()) if (array[i] < -1) return -2 - array[i]
            return -1
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
            vararg alignments: Alignment<S>
        ): MultiAlignmentHelper = build(settings, subjectRange, alignments[0].sequence1, *alignments)

        @JvmStatic
        @SafeVarargs
        fun <S : Sequence<S>> build(
            settings: Settings, subjectRange: Range,
            subject: S, vararg alignments: Alignment<S>
        ): MultiAlignmentHelper {
            for (alignment in alignments) require(alignment.sequence1 == subject)
            var subjectPointer = subjectRange.from
            val subjectPointerTo = subjectRange.to
            val aCount = alignments.size
            val queryPointers = IntArray(aCount) { alignments[it].sequence2Range.from }
            val mutationPointers = IntArray(aCount)
            val mutations: Array<Mutations<S>> = Array(aCount) { alignments[it].absoluteMutations }
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
                    if (!alignments[i].sequence1Range.contains(subjectPointer)
                        && !(alignments[i].sequence1Range.containsBoundary(subjectPointer) &&
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
            val queryPositionsArrays: Array<IntArray> = Array(aCount) {
                queryPositions[it].toArray()
            }
            val matchesBAs: Array<BitArray> = Array(aCount) {
                BitArray(matches[it])
            }
            val queryStringsArray: Array<String> = Array(aCount) {
                queryStrings[it].toString()
            }
            return MultiAlignmentHelper(
                subjectString.toString(), queryStringsArray, subjectPositions.toArray(),
                queryPositionsArrays, matchesBAs
            )
        }

        fun alignPositions(helpers: Array<MultiAlignmentHelper>) {
            var maxPositionWidth = 0
            for (helper in helpers)
                maxPositionWidth = max(maxPositionWidth, helper.actualPositionWidth)
            for (helper in helpers)
                helper.minimalPositionWidth = maxPositionWidth
        }

        private fun spaces(n: Int): String {
            val c = CharArray(n)
            Arrays.fill(c, ' ')
            return String(c)
        }
    }
}
