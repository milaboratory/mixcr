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
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsFormatter
import com.milaboratory.mixcr.util.and
import gnu.trove.set.hash.TIntHashSet
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import picocli.CommandLine
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*

@CommandLine.Command(
    name = "exportClonesPretty",
    sortOptions = true,
    separator = " ",
    description = ["Export verbose information about clones."]
)
class CommandExportClonesPretty : MiXCRCommand() {
    @CommandLine.Parameters(index = "0", description = ["clones.[clns|clna]"])
    lateinit var `in`: String

    @CommandLine.Parameters(index = "1", description = ["output.txt"], arity = "0..1")
    var out: String? = null

    @CommandLine.Option(description = ["Limit number of alignments before filtering"], names = ["-b", "--limitBefore"])
    var limitBefore: Int? = null

    @CommandLine.Option(
        description = ["Limit number of filtered alignments; no more " +
                "than N alignments will be outputted"], names = ["-n", "--limit"]
    )
    var limitAfter: Int? = null

    @CommandLine.Option(description = ["List of clone ids to export"], names = ["-i", "--clone-ids"])
    var ids: List<Int> = mutableListOf()

    @CommandLine.Option(description = ["Number of output alignments to skip"], names = ["-s", "--skip"])
    var skipAfter: Int? = null

    @CommandLine.Option(
        description = ["Filter export to a specific protein chain gene (e.g. TRA or IGH)."],
        names = ["-c", "--chains"]
    )
    var chain = "ALL"

    @CommandLine.Option(
        description = ["Only output clones where CDR3 (not whole clonal sequence) exactly equals to given sequence"],
        names = ["-e", "--cdr3-equals"]
    )
    var cdr3Equals: String? = null

    @CommandLine.Option(
        description = ["Only output clones where target clonal sequence contains sub-sequence."],
        names = ["-r", "--clonal-sequence-contains"]
    )
    var csContain: String? = null

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOfNotNull(out)

    private val cloneIds: TIntHashSet?
        get() = if (ids.isEmpty()) null else TIntHashSet(ids)

    private fun mkFilter(): Filter<Clone> {
        val chains = Chains.parse(chain)
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
            val seq = NucleotideSequence(cdr3Equals)
            resultFilter = resultFilter.and { clone: Clone ->
                clone.getFeature(GeneFeature.CDR3)?.sequence == seq
            }
        }
        return resultFilter
    }

    override fun run0() {
        val filter = mkFilter()
        var total: Long = 0
        var filtered: Long = 0
        val cloneSet = CloneSetIO.read(`in`)
        (out?.let { PrintStream(BufferedOutputStream(FileOutputStream(it), 32768)) } ?: System.out).use { output ->
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
                val targetAsMultiAlignment =
                    VDJCAlignmentsFormatter.getTargetAsMultiAlignment(clone, i, true, false) ?: continue
                val split = targetAsMultiAlignment.split(80)
                for (spl in split) {
                    output.println(spl)
                    output.println()
                }
            }
            output.println()
        }
    }
}
