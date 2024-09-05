/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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

import cc.redberry.pipe.util.asSequence
import cc.redberry.primitives.Filter
import cc.redberry.primitives.and
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.tag.TagsInfo
import com.milaboratory.mitool.tag.extractSequence
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.afiltering.AFilter
import com.milaboratory.util.NSequenceWithQualityPrintHelper
import gnu.trove.set.hash.TLongHashSet
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.PrintStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.outputStream

@Command(
    description = ["Export verbose information about alignments."]
)
class CommandExportAlignmentsPretty : MiXCRCommandWithOutputs() {
    @Parameters(
        index = "0",
        paramLabel = "alignments.(vdjca|clna)",
        description = ["Path to input file with alignments."]
    )
    lateinit var input: Path

    @Parameters(
        index = "1",
        paramLabel = "output.txt",
        arity = "0..1",
        description = ["Path where to write export. Will write to output if omitted."]
    )
    var out: Path? = null

    @Option(
        description = ["Output only top hits"],
        names = ["-t", "--top"],
        order = OptionsOrder.main + 10_100
    )
    var onlyTop = false

    @Option(
        description = ["Output full gene sequence"],
        names = ["-a", "--gene"],
        order = OptionsOrder.main + 10_200
    )
    var geneSequence = false

    @Option(
        description = ["Limit number of alignments before filtering"],
        names = ["-b", "--limit-before"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_300
    )
    var limitBefore: Int? = null

    @Option(
        description = ["Limit number of filtered alignments; no more than N alignments will be outputted"],
        names = ["-n", "--limit"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_400
    )
    var limitAfter: Int? = null

    @Option(
        description = ["Number of output alignments to skip"],
        names = ["-s", "--skip"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_500
    )
    var skipAfter: Int? = null

    @Option(
        description = ["Filter export to a specific protein chain gene (e.g. TRA or IGH)."],
        names = ["-c", "--chains"],
        paramLabel = Labels.CHAINS,
        showDefaultValue = ALWAYS,
        order = OptionsOrder.main + 10_600,
        completionCandidates = ChainsCandidates::class
    )
    var chains: Chains = Chains.ALL

    @Option(
        description = ["Output only alignments where CDR3 exactly equals to given sequence"],
        names = ["-e", "--cdr3-equals"],
        paramLabel = "<seq>",
        order = OptionsOrder.main + 10_700
    )
    var cdr3Equals: NucleotideSequence? = null

    @Option(
        description = ["Output only alignments which contain a corresponding gene feature"],
        names = ["-g", "--feature"],
        paramLabel = Labels.GENE_FEATURE,
        order = OptionsOrder.main + 10_800,
        completionCandidates = GeneFeaturesCandidates::class
    )
    var feature: GeneFeature? = null

    @Option(
        description = ["Output only alignments which don't contain a corresponding gene feature"],
        names = ["-ng", "--no-feature"],
        paramLabel = Labels.GENE_FEATURE,
        order = OptionsOrder.main + 10_850,
        completionCandidates = GeneFeaturesCandidates::class
    )
    var noFeature: GeneFeature? = null

    @Option(
        description = ["Output only alignments where target read contains a given substring"],
        names = ["-r", "--read-contains"],
        paramLabel = "<seq>",
        order = OptionsOrder.main + 10_900
    )
    var readContains: NucleotideSequence? = null

    @Option(
        description = ["Custom filter"],
        names = ["--filter"],
        paramLabel = "<s>",
        order = OptionsOrder.main + 11_000
    )
    var filter: String? = null

    @Option(
        description = ["Print read descriptions"],
        names = ["-d", "--descriptions"],
        order = OptionsOrder.main + 11_100
    )
    var printDescriptions = false

    @Option(
        description = ["List of read ids to export"],
        names = ["-i", "--read-ids"],
        paramLabel = "<id>",
        order = OptionsOrder.main + 11_200
    )
    var readIds: List<Long> = mutableListOf()

    @Option(
        description = ["List of clone ids to export"],
        names = ["--clone-ids"],
        paramLabel = "<id>",
        order = OptionsOrder.main + 11_300
    )
    var cloneIds: List<Long> = mutableListOf()

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOfNotNull(out)

    override fun initialize() {
        if (out == null)
            logger.redirectSysOutToSysErr()
    }

    private fun getReadIds(): TLongHashSet? = if (readIds.isEmpty()) null else TLongHashSet(readIds)

    private fun getCloneIds(): TLongHashSet? = if (cloneIds.isEmpty()) null else TLongHashSet(cloneIds)

    private fun mkFilter(): Filter<VDJCAlignments> {
        var resultFilter: Filter<VDJCAlignments> = Filter { true }
        if (filter != null) {
            resultFilter = resultFilter.and(AFilter.build(filter))
        }
        resultFilter = resultFilter.and { vdjcAlignments ->
            GeneType.VJC_REFERENCE
                .mapNotNull { vdjcAlignments.getBestHit(it) }
                .any { bestHit -> chains.intersects(bestHit.gene.chains) }
        }
        val readIds = getReadIds()
        if (readIds != null) {
            resultFilter = resultFilter.and { vdjcAlignments ->
                vdjcAlignments.readIds.any { it in readIds }
            }
        }
        val cloneIds = getCloneIds()
        if (cloneIds != null) {
            resultFilter = resultFilter.and { vdjcAlignments -> cloneIds.contains(vdjcAlignments.cloneIndex) }
        }
        feature?.let { feature ->
            resultFilter = resultFilter.and { vdjcAlignments ->
                (vdjcAlignments.getFeature(feature)?.size() ?: 0) > 0
            }
        }
        noFeature?.let { noFeature ->
            resultFilter = resultFilter.and { vdjcAlignments ->
                (vdjcAlignments.getFeature(noFeature)?.size() ?: 0) == 0
            }
        }
        if (readContains != null) {
            resultFilter = resultFilter.and { vdjcAlignments ->
                (0 until vdjcAlignments.numberOfTargets())
                    .map { i -> vdjcAlignments.getTarget(i).sequence }
                    .any { sequence -> sequence.toString().contains(readContains!!.toString()) }
            }
        }
        if (cdr3Equals != null) {
            resultFilter = resultFilter.and { vdjcAlignments ->
                vdjcAlignments.getFeature(CDR3)?.sequence == cdr3Equals
            }
        }
        return resultFilter
    }

    override fun validate() {
        ValidationException.requireFileType(input, InputFileType.VDJCA, InputFileType.CLNA)
        ValidationException.requireFileType(out, InputFileType.TXT)
    }

    override fun run1() {
        val filter = mkFilter()
        var total: Long = 0
        var filtered: Long = 0
        CommandExportAlignments.openAlignmentsPort(input).use { readerAndHeader ->
            ValidationException.chainsExist(chains, readerAndHeader.usedGenes)

            (out?.let { PrintStream(it.outputStream().buffered(32768)) } ?: System.out).use { output ->
                readerAndHeader.port.use { reader ->
                    val countBefore = limitBefore ?: Int.MAX_VALUE
                    val countAfter = limitAfter ?: Int.MAX_VALUE
                    val skipAfter = skipAfter ?: 0
                    reader
                        .asSequence()
                        .take(countBefore)
                        .onEach { ++total }
                        .filter { filter.accept(it) }
                        .drop(skipAfter)
                        .take(countAfter)
                        .forEach { alignments ->
                            ++filtered
                            val tagsInfo = readerAndHeader.info.header.tagsInfo
                            when {
                                logger.verbose -> outputVerbose(output, alignments, tagsInfo)
                                else -> outputCompact(output, alignments, tagsInfo)
                            }
                        }


                    output.println("Filtered: " + filtered + " / " + total + " = " + 100.0 * filtered / total + "%")
                }
            }
        }
    }

    fun outputCompact(output: PrintStream, alignments: VDJCAlignments, tagsInfo: TagsInfo) {
        output.println(
            ">>> Read ids: " + Arrays.toString(alignments.readIds)
                .replace("[", "")
                .replace("]", "")
        )
        if (alignments.cloneIndex != -1L) {
            output.print(">>> Clone mapping: ")
            output.print(alignments.cloneIndex)
            output.print(" ")
            output.println(alignments.getMappingType())
        }
        output.printTags(tagsInfo, alignments)
        output.println()
        output.println()
        for (i in 0 until alignments.numberOfTargets()) {
            if (printDescriptions) {
                output.println(
                    """
    >>> Assembly history: ${alignments.getHistory(i)}
    
    """.trimIndent()
                )
            }
            val targetAsMultiAlignment = MultiAlignmentHelper.Builder
                .formatMultiAlignments(alignments, i, addReads = alignments.getOriginalSequences() != null)
            val split = targetAsMultiAlignment.split(80)
            for (spl in split) {
                output.println(spl.format())
                output.println()
            }
        }
    }

    private fun outputVerbose(output: PrintStream, alignments: VDJCAlignments, tagsInfo: TagsInfo) {
        output.println(
            ">>> Read ids: " + Arrays.toString(alignments.readIds)
                .replace("[", "")
                .replace("]", "")
        )
        output.printTags(tagsInfo, alignments)
        output.println()
        output.println(">>> Target sequences (input sequences):")
        output.println()
        for (i in 0 until alignments.numberOfTargets()) {
            output.println("Sequence$i:")
            val partitionedTarget = alignments.getPartitionedTarget(i)
            printGeneFeatures(output, "Contains features: ") { geneFeature ->
                partitionedTarget.partitioning.isAvailable(geneFeature)
            }
            output.println()
            output.print(NSequenceWithQualityPrintHelper(alignments.getTarget(i), LINE_OFFSET, LINE_LENGTH))
        }
        if (alignments.numberOfTargets() > 1) {
            // Printing a set of available gene features for a full read
            output.println(">>> Gene features that can be extracted from this paired-read: ")
            printGeneFeatures(output, "") { geneFeature ->
                alignments.getFeature(geneFeature) != null
            }
        }
        output.println()
        for (geneType in GeneType.values()) {
            output.println(">>> Alignments with " + geneType.letter + " gene:")
            output.println()
            var exists = false
            var hits = alignments.getHits(geneType)
            if (hits.isNotEmpty()) {
                hits = if (onlyTop) arrayOf(hits[0]) else hits
                for (hit in hits) {
                    exists = true
                    output.println(hit.gene.name.name + " (total score = " + hit.score + ")")
                    for (i in 0 until alignments.numberOfTargets()) {
                        val alignment = hit.getAlignment(i) ?: continue
                        output.println("Alignment of Sequence" + i + " (score = " + alignment.score + "):")
                        for (subHelper in alignment.alignmentHelper.split(LINE_LENGTH, LINE_OFFSET)) {
                            output.println(subHelper.toStringWithSeq2Quality(alignments.getTarget(i).quality))
                            output.println()
                        }
                        if (geneSequence) {
                            output.println("Gene sequence:")
                            output.println(alignment.sequence1)
                            output.println()
                        }
                    }
                }
            }
            if (!exists) {
                output.println("No hits.")
                output.println()
            }
        }
        val ll = CharArray(94)
        Arrays.fill(ll, '=')
        output.println(ll)
        output.println()
    }

    private fun PrintStream.printTags(tagsInfo: TagsInfo, alignments: VDJCAlignments) {
        if (tagsInfo != TagsInfo.NO_TAGS) {
            println()
            println(">>> Tags:")
            tagsInfo.forEach { tag ->
                val tagValue = alignments.tagCount.singleOrNull(tag)?.extractKey()
                val toPrint = tagValue?.extractSequence() ?: tagValue.toString()
                println(">>> ${tag.name}: $toPrint")
            }
        }
    }

    private fun printGeneFeatures(output: PrintStream, prefix: String, containsFilter: (GeneFeature) -> Boolean) {
        output.print(prefix)
        var totalLength = prefix.length
        var first = true
        for (geneFeature in GeneFeature.getNameByFeature().keys) {
            if (!containsFilter(geneFeature)) continue
            if (first) {
                first = false
            } else {
                output.print(", ")
            }
            val name = GeneFeature.getNameByFeature(geneFeature)
            if (totalLength + name.length + 2 >= MAX_LENGTH) {
                output.println()
                totalLength = 0
            }
            output.print(name)
            totalLength += name.length + 2
        }
        output.println()
    }

    companion object {
        const val LINE_LENGTH = 80
        const val LINE_OFFSET = 7
        const val MAX_LENGTH = 2 * LINE_OFFSET + LINE_LENGTH
    }
}
