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
package com.milaboratory.mixcr.cli

import com.milaboratory.core.io.sequence.SequenceRead
import com.milaboratory.core.sequence.NSQTuple
import com.milaboratory.mitool.pattern.search.MicRecord
import com.milaboratory.mitool.pattern.search.ReadSearchMode
import com.milaboratory.mitool.pattern.search.ReadSearchPlan
import com.milaboratory.mitool.pattern.search.ReadSearchSettings
import com.milaboratory.mitool.pattern.search.ReadTagShortcut
import com.milaboratory.mitool.pattern.search.SearchSettings
import com.milaboratory.mitool.report.ParseReportAggregator
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.tag.LongTagValue
import com.milaboratory.mixcr.basictypes.tag.SequenceAndQualityTagValue
import com.milaboratory.mixcr.basictypes.tag.StringTagValue
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagValue
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.Good
import gnu.trove.impl.Constants
import gnu.trove.map.hash.TObjectIntHashMap
import jetbrains.datalore.plot.config.asMutable
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.regex.Matcher
import java.util.regex.Pattern

object CommandAlignPipeline {
    const val cellSplitGroupLabel = "CELLSPLIT"

    private val readGroupPattern = Regex("R\\d+")

    fun getTagsExtractor(
        cmdParams: CommandAlign.Params,
        fileTags: List<String>
    ): TagsExtractor {
        var plan: ReadSearchPlan? = null
        val readTags = mutableListOf<String>()
        var readTagShortcuts: List<ReadTagShortcut>? = null
        var tagExtractors = mutableListOf<TagExtractorWithInfo>()
        val sampleTable = cmdParams.sampleTable

        if (cmdParams.tagPattern != null) {
            val searchSettings = ReadSearchSettings(
                SearchSettings.Default.copy(bitBudget = cmdParams.tagMaxBudget),
                if (cmdParams.tagUnstranded) ReadSearchMode.DirectAndReversed else ReadSearchMode.Direct
            )
            plan = ReadSearchPlan.create(cmdParams.tagPattern, searchSettings)
            for (tagName in plan.allTags)
                if (tagName.matches(readGroupPattern))
                    readTags += tagName
                else {
                    val type = detectTagTypeByName(tagName) ?: continue
                    tagExtractors += TagExtractorWithInfo(
                        PatternTag(plan.tagShortcut(tagName)),
                        TagInfo(type, TagValueType.SequenceAndQuality, tagName, 0 /* will be changed below */)
                    )
                }

            readTagShortcuts = readTags.map { name -> plan.tagShortcut(name) }
            if (readTagShortcuts.isEmpty())
                throw ValidationException("Tag pattern has no read (payload) groups, nothing to align.")
            if (readTagShortcuts.size > 2) throw ValidationException(
                "Tag pattern contains too many read groups, only R1 or R1+R2 combinations are supported."
            )
        }

        if (cmdParams.readIdAsCellTag) {
            if (fileTags != listOf(cellSplitGroupLabel))
                throw ValidationException(
                    "Exactly one cell splitting group is required in file name for " +
                            "read-id-as-cell-tag feature to work (i.e. \"my_file_R{{$cellSplitGroupLabel:n}}.fastq.gz\")"
                )
            tagExtractors += TagExtractorWithInfo(
                ReadIndex,
                TagInfo(TagType.Cell, TagValueType.NonSequence, "READ_IDX", 0 /* will be changed below */)
            )
        }

        tagExtractors = tagExtractors
            .sortedBy { it.tagInfo }
            .mapIndexed { i, tag -> tag.withInfoIndex(i) }
            .asMutable()

        if (tagExtractors.size != 0) {
            println("The following tags and their roles were recognised:")
            if (readTagShortcuts != null)
                println("  Payload tags: " + readTags.joinToString(", "))

            tagExtractors
                .groupBy { it.tagInfo.type }
                .forEach { (tagType: TagType, extractors: List<TagExtractorWithInfo>) ->
                    println("  $tagType tags: " + extractors.joinToString(", ") { it.tagInfo.name })
                }
        }

        val finalExtractors = tagExtractors
            .sortedBy { it.tagInfo }
            .mapIndexed { i, tag -> tag.withInfoIndex(i) }

        val originalTagsInfo = TagsInfo(0, *finalExtractors.map { it.tagInfo }.toTypedArray())

        val (tagMapper, tagsInfo) =
            sampleTable?.toTagMapper(originalTagsInfo) ?: (null to originalTagsInfo)

        return TagsExtractor(
            plan, readTagShortcuts,
            emptyList(),
            finalExtractors.map { it.tagExtractor },
            tagMapper,
            tagsInfo
        )
    }

    sealed interface TagExtractor {
        fun extract(
            originalReadId: Long,
            fileTags: List<Pair<String, String>>,
            headerMatches: List<Matcher>,
            patternMatch: MicRecord?
        ): TagValue
    }

    private data class PatternTag(val shortcut: ReadTagShortcut) : TagExtractor {
        override fun extract(
            originalReadId: Long,
            fileTags: List<Pair<String, String>>,
            headerMatches: List<Matcher>,
            patternMatch: MicRecord?
        ) = SequenceAndQualityTagValue(patternMatch!!.getTagValue(shortcut).value)
    }

    private data class FileTag(val tagName: String) : TagExtractor {
        override fun extract(
            originalReadId: Long,
            fileTags: List<Pair<String, String>>,
            headerMatches: List<Matcher>,
            patternMatch: MicRecord?
        ) = StringTagValue(fileTags.find { it.first == tagName }!!.second)
    }

    private data class HeaderTag(val patternIdx: Int, val groupName: String) : TagExtractor {
        override fun extract(
            originalReadId: Long,
            fileTags: List<Pair<String, String>>,
            headerMatches: List<Matcher>,
            patternMatch: MicRecord?
        ) = StringTagValue(headerMatches[patternIdx].group(groupName))
    }

    private object ReadIndex : TagExtractor {
        override fun extract(
            originalReadId: Long,
            fileTags: List<Pair<String, String>>,
            headerMatches: List<Matcher>,
            patternMatch: MicRecord?
        ) = LongTagValue(originalReadId)
    }

    data class HeaderPattern(val patter: Pattern, val readIndices: List<Int>?) {
        /** Returns non-null result if all the patterns were matched */
        fun parse(read: SequenceRead): Matcher? {
            for (i in (readIndices ?: (0 until read.numberOfReads()))) {
                val matcher = patter.matcher(read.getRead(i).description)
                if (matcher.find())
                    return matcher
            }
            return null
        }
    }

    data class TagExtractorWithInfo(val tagExtractor: TagExtractor, val tagInfo: TagInfo) {
        fun withInfoIndex(idx: Int) = copy(tagInfo = tagInfo.withIndex(idx))
    }

    class TagMapper(
        private val matchingTagIds: IntArray,
        private val mapping: TObjectIntHashMap<List<String>>,
        private val mappingValues: List<String>,
        private val originalTagMapping: LongArray,
        private val mappingValueIndex: Int
    ) {
        init {
            require(mappingValueIndex >= 0)
            require(mapping.size() == mappingValues.size)
        }

        val miss = AtomicLong()
        val matches = AtomicLongArray(mapping.size())

        fun apply(values: List<TagValue>): List<TagValue>? {
            // Creating row of string to map against the mapping table
            val row = ArrayList<String>(matchingTagIds.size)
            for (i in matchingTagIds)
                row.add(values[i].toString())

            // Do mapping
            val match = mapping[row]
            if (match == -1) {
                miss.incrementAndGet()
                return null
            }
            matches.incrementAndGet(match)

            // Creating mapped tag values vector
            val result = arrayOfNulls<TagValue>(originalTagMapping.size + 1)
            result[mappingValueIndex] = StringTagValue(mappingValues[match])
            for (m in originalTagMapping)
                result[(m and 0xFFFFFFFFL).toInt()] = values[(m ushr 32).toInt()]

            return result.requireNoNulls().toList()
        }
    }

    private fun CommandAlign.SampleTable.toTagMapper(originalInfo: TagsInfo): Pair<TagMapper, TagsInfo> = run {
        val matchingTagIds = matchingTagNames
            .map {
                (originalInfo[it] ?: throw ValidationException("No tag with name \"$it\"")).index
            }.toIntArray()

        val mapping = TObjectIntHashMap<List<String>>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1)
        val mappingValues = ArrayList<String>(samples.size)
        samples.forEachIndexed { idx, row ->
            mapping.put(row.matchingTagValues, idx)
            mappingValues.add(row.sampleName)
        }

        val sampleTagInfo = TagInfo(TagType.Sample, TagValueType.NonSequence, sampleTagName, -1)
        val tagsInfosTmp =
            (originalInfo.filter { !matchingTagNames.contains(it.name) }.toList()
                    + listOf(sampleTagInfo)).sorted()
        var mappingValueIndex = -1
        val originalTagMapping = LongArray(tagsInfosTmp.size - 1)
        var i = 0
        val tagsInfosAfterMapping = tagsInfosTmp.mapIndexed { newIdx, tagInfo ->
            if (tagInfo.index == -1) {
                assert(mappingValueIndex == -1)
                mappingValueIndex = newIdx
            } else
                originalTagMapping[i++] = (tagInfo.index.toLong() shl 32) or newIdx.toLong()
            tagInfo.withIndex(newIdx)
        }
        TagMapper(matchingTagIds, mapping, mappingValues, originalTagMapping, mappingValueIndex) to
                TagsInfo(0, *tagsInfosAfterMapping.toTypedArray())
    }

    class TagsExtractor(
        /** Not null if tag pattern was specified */
        private val plan: ReadSearchPlan?,
        /** Not null if tag pattern was specified */
        private val readShortcuts: List<ReadTagShortcut>?,
        private val headerPatterns: List<HeaderPattern>,
        private val tagExtractors: List<TagExtractor>,
        val tagMapper: TagMapper?,
        val tagsInfo: TagsInfo
    ) {
        init {
            require((plan != null) == (readShortcuts != null))
        }

        val pairedPatternPayload = readShortcuts?.size?.let { it == 2 }

        val inputReads = AtomicLong()
        val matchedHeaders = AtomicLong()
        val reportAgg = plan?.let { ParseReportAggregator(it) }

        fun parse(bundle: ProcessingBundle): ProcessingBundle {
            inputReads.incrementAndGet()

            val headerMatches = headerPatterns.mapNotNull { it.parse(bundle.read) }
            if (headerMatches.size != headerPatterns.size)
                return bundle.copy(status = ProcessingBundleStatus.NotParsed)
            matchedHeaders.incrementAndGet()

            val (newSeq, patternMatch) =
                if (plan != null) {
                    val result = plan.search(bundle.read)
                    reportAgg!!.consume(result)
                    if (result.hit == null) return bundle.copy(status = ProcessingBundleStatus.NotParsed)
                    NSQTuple(
                        bundle.read.id,
                        *Array(readShortcuts!!.size) { i -> result.getTagValue(readShortcuts[i]).value }
                    ) to result
                } else
                    bundle.sequence to null

            val tags = tagExtractors
                .map {
                    it.extract(
                        bundle.originalReadId,
                        bundle.fileTags,
                        headerMatches,
                        patternMatch
                    )
                }

            val mappedTags =
                if (tagMapper == null)
                    tags
                else
                    tagMapper.apply(tags) ?: return bundle.copy(status = ProcessingBundleStatus.NotParsed)

            return bundle.copy(
                sequence = newSeq,
                tags = TagTuple(*tags.toTypedArray())
            )
        }
    }

    private fun detectTagTypeByName(name: String): TagType? =
        when {
            name.startsWith("S") -> TagType.Sample
            name.startsWith("CELL") -> TagType.Cell
            name.startsWith("UMI") || name.startsWith("MI") -> TagType.Molecule
            else -> {
                logger.warn("Can't recognize tag type for name \"$name\", this tag will be ignored during analysis.")
                null
            }
        }

    enum class ProcessingBundleStatus {
        Good,
        NotParsed,
        NotAligned,
    }

    data class ProcessingBundle(
        val read: SequenceRead,
        val fileTags: List<Pair<String, String>> = emptyList(),
        val originalReadId: Long = read.id,
        val sequence: NSQTuple = read.toTuple(),
        val tags: TagTuple = TagTuple.NO_TAGS,
        val alignment: VDJCAlignments? = null,
        val status: ProcessingBundleStatus = Good,
    ) {
        val ok get() = status == Good
        fun mapSequence(mapping: (NSQTuple) -> NSQTuple) = copy(sequence = mapping(sequence))
    }
}