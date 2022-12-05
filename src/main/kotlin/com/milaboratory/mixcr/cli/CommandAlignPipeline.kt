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
import jetbrains.datalore.plot.config.asMutable
import java.util.*
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
            if (sampleTable != null)
                println("The following tags and their roles were recognised before sample mapping:")
            else
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

        if (originalTagsInfo.any { it.type == TagType.Sample } && sampleTable == null)
            throw ValidationException("Sample barcodes without sample table are not supported")

        val (tagMapper, tagsInfo) =
            sampleTable?.toTagMapper(originalTagsInfo, !cmdParams.splitBySample) ?: (null to originalTagsInfo)

        if (sampleTable != null) {
            println("The following tags and their roles were recognised after sample mapping:")
            tagsInfo
                .groupBy { it.type }
                .forEach { (tagType: TagType, extractors: List<TagInfo>) ->
                    println("  $tagType tags: " + extractors.joinToString(", ") { it.name })
                }
        }

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

    /** Represents single matcher for tag mapper. Matchers are tried in order,
     * until the match is found or matchers list is exhausted */
    class TagMapperMatcher(
        /** Not null values will only match specific pattern variant id */
        private val variantId: Int?,
        /** Expected values for tags in certain positions, null match any value */
        private val tagValues: List<String?>,
        /** i.e. sample name index */
        val mappingIdx: Int,
    ) {
        init {
            require(variantId != null || tagValues.any { it != null })
            require(mappingIdx >= 0)
        }

        /** Returns non-null value with sample name if matched the provided value,
         * or null if not matched */
        fun match(variantId: Int, tagValues: List<String>): Boolean {
            if (this.variantId != null && this.variantId != variantId) return false
            this.tagValues.forEachIndexed { i, v -> if (v != null && v != tagValues[i]) return false }
            return true
        }
    }

    class TagMapper(
        private val matchingTagIds: IntArray,
        private val matchers: List<TagMapperMatcher>,
        val samples: List<String>,
        private val originalTagMapping: IntArray,
        private val mappingValueIndex: Int,
        private val newTagsCount: Int
    ) {
        init {
            require(samples.toSet().size == samples.size)
        }

        val miss = AtomicLong()
        val matches = AtomicLongArray(samples.size)

        val sampleStat get() = samples.mapIndexed { i, s -> s to matches[i] }.toMap(TreeMap())

        fun apply(variantId: Int, oldTags: List<TagValue>): Pair<List<TagValue>, String>? {
            // Creating row of string to map against the mapping table
            val row = ArrayList<String>(matchingTagIds.size)
            for (i in matchingTagIds)
                row.add(oldTags[i].extractKey().toString())

            // Do mapping
            val matched = matchers.firstOrNull { it.match(variantId, row) }
            if (matched == null) {
                miss.incrementAndGet()
                return null
            }
            matches.incrementAndGet(matched.mappingIdx)

            // Creating mapped tag values vector
            val result = arrayOfNulls<TagValue>(newTagsCount)
            val sample = samples[matched.mappingIdx]
            if (mappingValueIndex != -1)
                result[mappingValueIndex] = StringTagValue(sample)
            for (oldIdx in originalTagMapping.indices) {
                val newIdx = originalTagMapping[oldIdx]
                if (newIdx != -1)
                    result[newIdx] = oldTags[oldIdx]
            }

            return result.requireNoNulls().toList() to sample
        }
    }

    private fun CommandAlign.SampleTable.toTagMapper(
        originalInfo: TagsInfo,
        addSampleTag: Boolean
    ): Pair<TagMapper, TagsInfo> = run {
        // Tags appearing at least once in the list of tags to be matched (sorted)
        val matchingTagNames = samples.flatMap { it.matchTags.keys }
            .toSortedSet()
            .toList()

        // All unique sample names (sorted)
        val sampleNames = samples.map { it.sampleName }
            .toSortedSet()
            .toList()

        val matchingTagIds = matchingTagNames
            .map {
                (originalInfo[it] ?: throw ValidationException("No tag with name \"$it\"")).index
            }.toIntArray()

        val matchers = samples.map { sample ->
            TagMapperMatcher(
                sample.matchVariantId,
                matchingTagNames.map { sample.matchTags[it] },
                sampleNames.indexOf(sample.sampleName)
            )
        }

        val sampleTagInfo =
            if (addSampleTag)
                listOf(TagInfo(TagType.Sample, TagValueType.NonSequence, sampleTagName, -1))
            else
                emptyList()

        val tagsInfosTmp =
            (originalInfo
                // Important note: now all the original sample tags are removed in all cases
                .filter { it.type != TagType.Sample }
                .toList()
                    + sampleTagInfo).sorted()

        var mappingValueIndex = -1
        val originalTagMapping = IntArray(originalInfo.size) { -1 }
        val tagsInfosAfterMapping = tagsInfosTmp.mapIndexed { newIdx, tagInfo ->
            if (tagInfo.index == -1) {
                assert(mappingValueIndex == -1)
                mappingValueIndex = newIdx
            } else {
                assert(originalTagMapping[tagInfo.index] == -1)
                originalTagMapping[tagInfo.index] = newIdx
            }
            tagInfo.withIndex(newIdx)
        }

        assert((mappingValueIndex != -1) == (addSampleTag))

        TagMapper(
            matchingTagIds,
            matchers,
            sampleNames,
            originalTagMapping,
            mappingValueIndex,
            tagsInfosAfterMapping.size
        ) to TagsInfo(0, *tagsInfosAfterMapping.toTypedArray())
    }

    class TagsExtractor(
        /** Not null if tag pattern was specified */
        private val plan: ReadSearchPlan?,
        /** Not null if tag pattern was specified */
        private val readShortcuts: List<ReadTagShortcut>?,
        private val headerPatterns: List<HeaderPattern>,
        private val tagExtractors: List<TagExtractor>,
        private val tagMapper: TagMapper?,
        val tagsInfo: TagsInfo
    ) {
        init {
            require((plan != null) == (readShortcuts != null))
            require(readShortcuts == null || readShortcuts.size <= 2) {
                "At most two payload reads are supported."
            }
        }

        val samples: List<String>? = tagMapper?.samples

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

            var variantId = -1

            val (newSeq, patternMatch) =
                if (plan != null) {
                    val result = plan.search(bundle.read)
                    reportAgg!!.consume(result)
                    if (result.hit == null) return bundle.copy(status = ProcessingBundleStatus.NotParsed)
                    variantId = result.hit!!.variantId
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

            val (mappedTags, sample) =
                if (tagMapper == null)
                    tags to ""
                else
                    tagMapper.apply(variantId, tags)
                        ?: return bundle.copy(status = ProcessingBundleStatus.SampleNotMatched)

            return bundle.copy(
                sequence = newSeq,
                tags = TagTuple(*mappedTags.toTypedArray()),
                sample = sample
            )
        }

        val sampleStat get() = tagMapper?.sampleStat
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
        SampleNotMatched,
        NotAligned,
    }

    data class ProcessingBundle(
        val read: SequenceRead,
        val fileTags: List<Pair<String, String>> = emptyList(),
        val originalReadId: Long = read.id,
        val sequence: NSQTuple = read.toTuple(),
        val tags: TagTuple = TagTuple.NO_TAGS,
        val sample: String = "",
        val alignment: VDJCAlignments? = null,
        val status: ProcessingBundleStatus = Good,
    ) {
        val ok get() = status == Good
        fun mapSequence(mapping: (NSQTuple) -> NSQTuple) = copy(sequence = mapping(sequence))
    }
}