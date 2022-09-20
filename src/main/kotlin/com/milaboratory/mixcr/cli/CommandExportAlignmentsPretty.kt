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

import cc.redberry.primitives.Filter
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsFormatter
import com.milaboratory.mixcr.cli.afiltering.AFilter
import com.milaboratory.mixcr.util.and
import com.milaboratory.primitivio.asSequence
import com.milaboratory.util.NSequenceWithQualityPrintHelper
import gnu.trove.set.hash.TLongHashSet
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import picocli.CommandLine
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*

@CommandLine.Command(
    name = "exportAlignmentsPretty",
    sortOptions = true,
    separator = " ",
    description = ["Export verbose information about alignments."]
)
class CommandExportAlignmentsPretty : AbstractMiXCRCommand() {
    @CommandLine.Parameters(index = "0", description = ["alignments.vdjca"])
    lateinit var `in`: String

    @CommandLine.Parameters(index = "1", description = ["output.txt"], arity = "0..1")
    var out: String? = null

    @CommandLine.Option(description = ["Output only top hits"], names = ["-t", "--top"])
    var onlyTop = false

    @CommandLine.Option(description = ["Output full gene sequence"], names = ["-a", "--gene"])
    var geneSequence = false

    @CommandLine.Option(description = ["Limit number of alignments before filtering"], names = ["-b", "--limit-before"])
    var limitBefore: Int? = null

    @CommandLine.Option(
        description = ["Limit number of filtered alignments; no more " +
                "than N alignments will be outputted"], names = ["-n", "--limit"]
    )
    var limitAfter: Int? = null

    @CommandLine.Option(
        description = ["Filter export to a specific protein chain gene (e.g. TRA or IGH)."],
        names = ["-c", "--chains"]
    )
    var chain = "ALL"

    @CommandLine.Option(description = ["Number of output alignments to skip"], names = ["-s", "--skip"])
    var skipAfter: Int? = null

    @CommandLine.Option(
        description = ["Output only alignments where CDR3 exactly equals to given sequence"],
        names = ["-e", "--cdr3-equals"]
    )
    var cdr3Equals: String? = null

    @CommandLine.Option(
        description = ["Output only alignments which contain a corresponding gene feature"],
        names = ["-g", "--feature"]
    )
    var feature: String? = null

    @CommandLine.Option(
        description = ["Output only alignments where target read contains a given substring"],
        names = ["-r", "--read-contains"]
    )
    var readContains: String? = null

    @CommandLine.Option(description = ["Custom filter"], names = ["--filter"])
    var filter: String? = null

    @CommandLine.Option(description = ["Print descriptions"], names = ["-d", "--descriptions"])
    var printDescriptions = false

    @CommandLine.Option(description = ["List of read ids to export"], names = ["-i", "--read-ids"])
    var readIds: List<Long> = mutableListOf()

    // @CommandLine.Option(description = ["Alignment index"], names = ["--alignment-idx"])
    // var alignmentIdx: List<Long> = mutableListOf()

    @CommandLine.Option(description = ["List of clone ids to export"], names = ["--clone-ids"])
    var cloneIds: List<Long> = mutableListOf()

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOfNotNull(out)

    private fun getReadIds(): TLongHashSet? = if (readIds.isEmpty()) null else TLongHashSet(readIds)

    private fun getCloneIds(): TLongHashSet? = if (cloneIds.isEmpty()) null else TLongHashSet(cloneIds)

    private fun mkFilter(): Filter<VDJCAlignments> {
        val chains = Chains.parse(chain)
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
        if (feature != null) {
            val feature = GeneFeature.parse(feature)
            resultFilter = resultFilter.and { vdjcAlignments ->
                (vdjcAlignments.getFeature(feature)?.size() ?: 0) > 0
            }
        }
        if (readContains != null) {
            resultFilter = resultFilter.and { vdjcAlignments ->
                (0 until vdjcAlignments.numberOfTargets())
                    .map { i -> vdjcAlignments.getTarget(i).sequence }
                    .any { sequence -> sequence.toString().contains(readContains!!) }
            }
        }
        if (cdr3Equals != null) {
            val seq = NucleotideSequence(cdr3Equals)
            resultFilter = resultFilter.and { vdjcAlignments ->
                vdjcAlignments.getFeature(CDR3)?.sequence == seq
            }
        }
        return resultFilter
    }

    override fun run0() {
        val filter = mkFilter()
        var total: Long = 0
        var filtered: Long = 0
        CommandExportAlignments.openAlignmentsPort(`in`).use { readerAndHeader ->
            (out?.let { PrintStream(BufferedOutputStream(FileOutputStream(it), 32768)) } ?: System.out).use { output ->
                val reader = readerAndHeader.port
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
                        if (verbose) outputVerbose(output, alignments) else outputCompact(output, alignments)
                    }

                output.println("Filtered: " + filtered + " / " + total + " = " + 100.0 * filtered / total + "%")
            }
        }
    }

    fun outputCompact(output: PrintStream, alignments: VDJCAlignments) {
        output.println(
            ">>> Read ids: " + Arrays.toString(alignments.readIds)
                .replace("[", "")
                .replace("]", "")
        )
        if (alignments.cloneIndex != -1L) {
            output.print(">>> Clone mapping: ")
            output.print(alignments.cloneIndex)
            output.print(" ")
            output.println(alignments.mappingType)
        }
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
            val targetAsMultiAlignment = VDJCAlignmentsFormatter.getTargetAsMultiAlignment(alignments, i) ?: continue
            val split = targetAsMultiAlignment.split(80)
            for (spl in split) {
                output.println(spl)
                output.println()
            }
        }
    }

    private fun outputVerbose(output: PrintStream, alignments: VDJCAlignments) {
        output.println(
            ">>> Read ids: " + Arrays.toString(alignments.readIds)
                .replace("[", "")
                .replace("]", "")
        )
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
                    output.println(hit.gene.name + " (total score = " + hit.score + ")")
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

    private fun printGeneFeatures(output: PrintStream, prefix: String, containsFilter: Filter<GeneFeature>) {
        output.print(prefix)
        var totalLength = prefix.length
        var first = true
        for (geneFeature in GeneFeature.getNameByFeature().keys) {
            if (!containsFilter.accept(geneFeature)) continue
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
