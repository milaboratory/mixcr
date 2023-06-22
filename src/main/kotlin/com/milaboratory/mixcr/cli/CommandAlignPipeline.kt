/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.app.ValidationException
import com.milaboratory.core.io.sequence.SequenceRead
import com.milaboratory.core.sequence.NSQTuple
import com.milaboratory.mitool.container.MicRecord
import com.milaboratory.mitool.container.ReadTagShortcut
import com.milaboratory.mitool.pattern.search.ReadSearchMode
import com.milaboratory.mitool.pattern.search.ReadSearchPlan
import com.milaboratory.mitool.pattern.search.ReadSearchSettings
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
import com.milaboratory.mixcr.basictypes.tag.TechnicalTag.TAG_PATTERN_READ_VARIANT_ID
import com.milaboratory.mixcr.basictypes.tag.suffixInfo
import com.milaboratory.mixcr.cli.CommandAlignParams.Companion.allTagTransformationSteps
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.Good
import com.milaboratory.util.UNNAMED_GROUP_NAME_PREFIX
import com.milaboratory.util.listComparator
import jetbrains.datalore.plot.config.asMutable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.regex.Matcher
import java.util.regex.Pattern

object CommandAlignPipeline {
    const val cellSplitGroupLabel = "CELLSPLIT"

    private val readGroupPattern = Regex("R\\d+")

    fun getTagsExtractor(
        cmdParams: CommandAlignParams,
        fileGroups: CommandAlign.InputFileGroups
    ): TagsExtractor {
        val plan: ReadSearchPlan?
        val readTags = mutableListOf<String>()
        val readTagShortcuts: List<ReadTagShortcut>?
        var tagExtractors = mutableListOf<TagExtractorWithInfo>()
        val transformationSteps = cmdParams.allTagTransformationSteps

        // Tag pattern parsing
        if (cmdParams.tagPattern != null) {
            val searchSettings = ReadSearchSettings(
                SearchSettings.Default.copy(bitBudget = cmdParams.tagMaxBudget),
                if (cmdParams.tagUnstranded) ReadSearchMode.DirectAndReversed else ReadSearchMode.Direct
            )
            plan = ReadSearchPlan.create(cmdParams.tagPattern!!, searchSettings)
            for (tagName in plan.allTags)
                if (tagName.matches(readGroupPattern))
                    readTags += tagName
                else {
                    val type = TagType.detectByTagName(tagName) ?: continue
                    tagExtractors += TagExtractorWithInfo(
                        PatternTag(plan.tagShortcut(tagName)),
                        TagInfo(type, TagValueType.SequenceAndQuality, tagName, 0 /* will be changed below */)
                    )
                }

            tagExtractors += TagExtractorWithInfo(
                PatternVariantIdTag,
                TagInfo(
                    TagType.Technical,
                    TagValueType.NonSequence,
                    TAG_PATTERN_READ_VARIANT_ID,
                    0 /* will be changed below */
                )
            )

            readTagShortcuts = readTags.map { name -> plan.tagShortcut(name) }
            if (readTagShortcuts.isEmpty())
                throw ValidationException("Tag pattern has no read (payload) groups, nothing to align.")
            if (readTagShortcuts.size > 2) throw ValidationException(
                "Tag pattern contains too many read groups, only R1 or R1+R2 combinations are supported."
            )
        } else {
            plan = null
            readTagShortcuts = null
        }

        // Groups from input file names or "read-id-as-cell-tag" activation
        val fileTags = fileGroups.tags
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
        } else
            fileTags.forEach { tagName ->
                // Adding only those groups for which user specified a name
                if (tagName.startsWith(UNNAMED_GROUP_NAME_PREFIX))
                    return@forEach
                // Uncategorized groups are added as Technical, to be available for potential transformation below the pipeline
                val type = TagType.detectByTagName(tagName) ?: TagType.Technical
                tagExtractors += TagExtractorWithInfo(
                    FileTag(tagName),
                    TagInfo(type, TagValueType.NonSequence, tagName, 0 /* will be changed below */)
                )
            }

        // Sorting according to natural tag ordering
        tagExtractors = tagExtractors
            .sortedBy { it.tagInfo }
            .mapIndexed { i, tag -> tag.withInfoIndex(i) }
            .asMutable()

        val willHaveTransformers = transformationSteps.isNotEmpty()

        if (tagExtractors.size != 0 && willHaveTransformers) {
            println("The following tags and their roles were recognised before tag transformation:")
            if (readTagShortcuts != null)
                println("  Payload tags: " + readTags.joinToString(", "))
            tagExtractors
                .groupBy { it.tagInfo.type }
                // Technical tags are printed only if transformation will take place
                .filter { willHaveTransformers || it.key != TagType.Technical }
                .forEach { (tagType: TagType, extractors: List<TagExtractorWithInfo>) ->
                    println("  $tagType tags: " + extractors.joinToString(", ") { "${it.tagInfo.name}(${it.tagInfo.valueType.shortString})" })
                }
        }

        // Pre-calculating transformations provided by preset and mixins
        val preTransformExtractors = tagExtractors
            .sortedBy { it.tagInfo }
            .mapIndexed { i, tag -> tag.withInfoIndex(i) }
        val preTransformTagsInfo = TagsInfo(0, *preTransformExtractors.map { it.tagInfo }.toTypedArray())
        var currentTagsInfo = preTransformTagsInfo
        val transformers = mutableListOf<TagTransformer>()

        // If required also adding a transformer to remove all leftover technical tags
        for (transformationStep in (transformationSteps + TagTransformationSteps.CutTechnicalTags)) {
            val tr = transformationStep.createTransformer(currentTagsInfo) ?: continue
            currentTagsInfo = tr.outputTagsInfo
            transformers += tr
        }

        if (currentTagsInfo.size > 0) {
            println("The following tags and their roles will be associated with each output alignment:")

            if (readTagShortcuts != null)
                println("  Payload tags: " + readTags.joinToString(", "))
            currentTagsInfo
                .groupBy { it.type }
                .forEach { (tagType: TagType, infos: List<TagInfo>) ->
                    println("  $tagType tags: " + infos.joinToString(", ") { "${it.name}(${it.valueType.shortString})" })
                }
        }

        return TagsExtractor(
            plan, readTagShortcuts,
            emptyList(),
            tagExtractors.map { it.tagExtractor },
            transformers,
            cmdParams.splitBySample,
            currentTagsInfo,
        )
    }

    // fun inferSampleTable(fileGroups: CommandAlign.InputFileGroups): CommandAlignParams.SampleTable {
    //     val sampleTagNames = fileGroups.tags.filter { TagType.detectByTagName(it) == TagType.Sample }
    //     return CommandAlignParams.SampleTable(
    //         sampleTagNames,
    //         fileGroups.fileGroups
    //             .map { fg -> sampleTagNames.map { fg.getTag(it) } }
    //             .toSortedSet(listComparator())
    //             .map { sample ->
    //                 CommandAlignParams.SampleTable.Row(
    //                     matchTags = sampleTagNames
    //                         .mapIndexed { i, tn -> tn to sample[i] }
    //                         .toMap(TreeMap()),
    //                     sample = sample
    //                 )
    //             }
    //     )
    // }

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

    private object PatternVariantIdTag : TagExtractor {
        override fun extract(
            originalReadId: Long,
            fileTags: List<Pair<String, String>>,
            headerMatches: List<Matcher>,
            patternMatch: MicRecord?
        ) = LongTagValue(patternMatch!!.hit!!.variantId.toLong())
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
        val samples: List<List<String>>,
        private val originalTagMapping: IntArray,
        private val mappingValueIndices: IntArray,
        private val newTagsCount: Int
    ) {
        init {
            require(samples.toSet().size == samples.size)
        }

        val miss = AtomicLong()
        val matches = AtomicLongArray(samples.size)

        val sampleStat get() = samples.mapIndexed { i, s -> s to matches[i] }.toMap(TreeMap(listComparator()))

        fun apply(variantId: Int, oldTags: List<TagValue>): Pair<List<TagValue>, List<String>>? {
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
            mappingValueIndices.forEachIndexed { i, mIdx ->
                result[mIdx] = StringTagValue(sample[i])
            }
            for (oldIdx in originalTagMapping.indices) {
                val newIdx = originalTagMapping[oldIdx]
                if (newIdx != -1)
                    result[newIdx] = oldTags[oldIdx]
            }

            return result.requireNoNulls().toList() to sample
        }
    }

    class SampleStat(val sampleKey: List<String>) {
        val reads = AtomicLong()
        val hash = AtomicInteger()
    }

    class TagsExtractor(
        /** Not null if tag pattern was specified */
        private val plan: ReadSearchPlan?,
        /** Not null if tag pattern was specified */
        private val readShortcuts: List<ReadTagShortcut>?,
        private val headerPatterns: List<HeaderPattern>,
        private val tagExtractors: List<TagExtractor>,
        private val tagTransformers: List<TagTransformer>,
        private val isolateSamples: Boolean,
        tagsInfoAfterExtraction: TagsInfo
    ) {
        init {
            require((plan != null) == (readShortcuts != null))
            require(readShortcuts == null || readShortcuts.size <= 2) {
                "At most two payload reads are supported."
            }
        }

        private val sampleTagsDepth = tagsInfoAfterExtraction.getDepthFor(TagType.Sample)
        val tagsInfo =
            if (isolateSamples)
                tagsInfoAfterExtraction.suffixInfo(sampleTagsDepth)
            else
                tagsInfoAfterExtraction

        val pairedPatternPayload = readShortcuts?.size?.let { it == 2 }

        val inputReads = AtomicLong()
        private val matchedHeaders = AtomicLong()
        val reportAgg = plan?.let { ParseReportAggregator(it) }

        val sampleStats = ConcurrentHashMap<List<String>, SampleStat>()

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

            var tags = tagExtractors
                .map {
                    it.extract(
                        bundle.originalReadId,
                        bundle.fileTags,
                        headerMatches,
                        patternMatch
                    )
                }
                .toTypedArray()

            for (tagTransformer in tagTransformers)
                tags = tagTransformer.transform(tags)
                    ?: return bundle.copy(status = ProcessingBundleStatus.NotMatched)

            // String sample key for file splitting and stats
            val sampleKey = tags.copyOfRange(0, sampleTagsDepth)
                .map { it.extractKey().toString() }

            val sampleStat = sampleStats.computeIfAbsent(sampleKey) { key -> SampleStat(key) }
            sampleStat.reads.incrementAndGet()
            sampleStat.hash.addAndGet(newSeq.hashCode())

            return if (isolateSamples)
                bundle.copy(
                    sequence = newSeq,
                    tags = TagTuple(*tags.copyOfRange(sampleTagsDepth, tags.size)),
                    sample = sampleKey,
                )
            else
                bundle.copy(
                    sequence = newSeq,
                    tags = TagTuple(*tags),
                )
        }

        val transformerReports get() = tagTransformers.mapNotNull { it.report }
    }

    enum class ProcessingBundleStatus {
        Good,
        NotParsed,
        NotMatched,
        NotAligned,
    }

    data class ProcessingBundle(
        val read: SequenceRead,
        val fileTags: List<Pair<String, String>> = emptyList(),
        val originalReadId: Long = read.id,
        val sequence: NSQTuple = read.toTuple(),
        val tags: TagTuple = TagTuple.NO_TAGS,
        val sample: List<String> = emptyList(),
        val alignment: VDJCAlignments? = null,
        val status: ProcessingBundleStatus = Good,
    ) {
        val ok get() = status == Good
        fun mapSequence(mapping: (NSQTuple) -> NSQTuple) = copy(sequence = mapping(sequence))
    }
}
