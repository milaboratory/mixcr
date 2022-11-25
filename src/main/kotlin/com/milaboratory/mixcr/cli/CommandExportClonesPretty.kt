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
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.MultiAlignmentFormatter
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsFormatter
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.util.and
import gnu.trove.set.hash.TIntHashSet
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.*

@Command(
    description = ["Export verbose information about clones."]
)
class CommandExportClonesPretty : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Path to input file with clones"],
        index = "0",
        paramLabel = "clones.(clns|clna)"
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
        description = ["Limit number of alignments before filtering"],
        names = ["-b", "--limitBefore"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_100
    )
    var limitBefore: Int? = null

    @Option(
        description = ["Limit number of filtered alignments; no more than N alignments will be outputted"],
        names = ["-n", "--limit"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_200
    )
    var limitAfter: Int? = null

    @Option(
        description = ["Number of output alignments to skip"],
        names = ["-s", "--skip"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_300
    )
    var skipAfter: Int? = null

    @Option(
        description = ["List of clone ids to export"],
        names = ["-i", "--clone-ids"],
        paramLabel = "<id>",
        order = OptionsOrder.main + 10_400
    )
    var ids: List<Int> = mutableListOf()

    @Option(
        description = ["Filter export to a specific protein chain gene (e.g. TRA or IGH)."],
        names = ["-c", "--chains"],
        paramLabel = Labels.CHAINS,
        showDefaultValue = ALWAYS,
        order = OptionsOrder.main + 10_500
    )
    var chains: Chains = Chains.ALL

    @Option(
        description = ["Only output clones where CDR3 (not whole clonal sequence) exactly equals to given sequence"],
        names = ["-e", "--cdr3-equals"],
        paramLabel = "<seq>",
        order = OptionsOrder.main + 10_600
    )
    var cdr3Equals: NucleotideSequence? = null

    @Option(
        description = ["Only output clones where target clonal sequence contains sub-sequence."],
        names = ["-r", "--clonal-sequence-contains"],
        paramLabel = "<seq>",
        order = OptionsOrder.main + 10_700
    )
    var csContain: String? = null

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOfNotNull(out)

    private val cloneIds: TIntHashSet?
        get() = if (ids.isEmpty()) null else TIntHashSet(ids)

    private fun mkFilter(): Filter<Clone> {
        var resultFilter: Filter<Clone> = Filter { true }
        val cloneIds = cloneIds
        if (cloneIds != null) {
            resultFilter = resultFilter.and { clone: Clone -> clone.id in cloneIds }
        }
        resultFilter = resultFilter.and { clone: Clone ->
            GeneType.VJC_REFERENCE
                .mapNotNull { clone.getBestHit(it) }
                .any { bestHit -> chains.intersects(bestHit.gene.chains) }
        }
        if (csContain != null) {
            val csContain = csContain!!.uppercase(Locale.getDefault())
            resultFilter = resultFilter.and { clone: Clone ->
                (0 until clone.numberOfTargets())
                    .map { i -> clone.getTarget(i).sequence }
                    .any { sequence ->
                        sequence.toString().contains(csContain)
                    }
            }
        }
        if (cdr3Equals != null) {
            resultFilter = resultFilter.and { clone: Clone ->
                clone.getFeature(GeneFeature.CDR3)?.sequence == cdr3Equals
            }
        }
        return resultFilter
    }

    override fun validate() {
        ValidationException.requireFileType(input, InputFileType.CLNX)
    }

    override fun run0() {
        val filter = mkFilter()
        var total: Long = 0
        var filtered: Long = 0
        val cloneSet = CloneSetIO.read(input)
        (out?.let { PrintStream(BufferedOutputStream(FileOutputStream(it.toFile()), 32768)) }
            ?: System.out).use { output ->
            val countBefore = limitBefore ?: Int.MAX_VALUE
            val countAfter = limitAfter ?: Int.MAX_VALUE
            val skipAfter = skipAfter ?: 0
            cloneSet
                .asSequence()
                .take(countBefore)
                .onEach { ++total }
                .filter { filter.accept(it) }
                .drop(skipAfter)
                .take(countAfter)
                .forEach { clone ->
                    ++filtered
                    outputCompact(output, clone)
                }

            output.println("Filtered: " + filtered + " / " + total + " = " + 100.0 * filtered / total + "%")
        }
    }

    companion object {
        @JvmStatic
        fun outputCompact(output: PrintStream, clone: Clone) {
            output.println(">>> Clone id: " + clone.id)
            output.println(">>> Abundance, reads (fraction): " + clone.count + " (" + clone.fraction + ")")
            output.println()
            for (i in 0 until clone.numberOfTargets()) {
                val targetAsMultiAlignment = VDJCAlignmentsFormatter(addReads = false)
                    .formatMultiAlignments(clone, i)
                val split = targetAsMultiAlignment.split(80)
                for (spl in split) {
                    output.println(spl.format(MultiAlignmentFormatter.LinesFormatter(addHitScore = true)))
                    output.println()
                }
            }
            output.println()
        }
    }
}
