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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.CountLimitingOutputPort
import cc.redberry.primitives.Filter
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.primitivio.buffered
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.SmartProgressReporter
import gnu.trove.set.hash.TLongHashSet
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    name = CommandFilterAlignments.COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Filter alignments."]
)
class CommandFilterAlignments : MiXCRCommandWithOutputs() {
    @Parameters(description = ["alignments.vdjca"], index = "0")
    lateinit var input: Path

    @Parameters(description = ["alignments.filtered.vdjca"], index = "1")
    lateinit var out: Path

    @Option(
        description = ["Specifies immunological protein chain gene for an alignment. If many, " +
                "separated by ','. Available genes: IGH, IGL, IGK, TRA, TRB, TRG, TRD."], names = ["-c", "--chains"]
    )
    var chains = "ALL"

    @Option(
        description = ["Include only those alignments that contain specified feature."],
        names = ["-g", "--contains-feature"]
    )
    var containsFeature: String? = null

    @Option(
        description = ["Include only those alignments which CDR3 equals to a specified sequence."],
        names = ["-e", "--cdr3-equals"]
    )
    var cdr3Equals: String? = null

    @Option(description = ["Output only chimeric alignments."], names = ["-x", "--chimeras-only"])
    var chimerasOnly = false

    @set:Option(description = ["Maximal number of reads to process"], names = ["-n", "--limit"])
    var limit: Long = 0
        set(value) {
            if (value <= 0) throw ValidationException("-n / --limit must be positive.")
            field = value
        }

    @set:Option(description = ["List of read ids to export"], names = ["-i", "--read-ids"], hidden = true)
    var ids: List<Long>? = null
        set(value) {
            println("-i, --read-ids deprecated, use `mixcr slice -i ... alignments.vdjca alignments.filtered.vdjca` instead")
            field = value
        }

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOf(out)

    private val readIds: TLongHashSet?
        get() = ids?.let { TLongHashSet(it) }

    private val inputReader: VDJCAlignmentsReader
        get() = VDJCAlignmentsReader(input)

    private val outputWriter: VDJCAlignmentsWriter
        get() = VDJCAlignmentsWriter(out)

    private val containFeature: GeneFeature?
        get() = if (containsFeature == null) null else GeneFeature.parse(containsFeature)

    private fun getCdr3Equals(): NucleotideSequence? =
        if (cdr3Equals == null) null else NucleotideSequence(cdr3Equals)

    private val filter: AlignmentsFilter by lazy {
        AlignmentsFilter(
            containFeature, getCdr3Equals(), Chains.parse(chains),
            readIds, chimerasOnly
        )
    }

    override fun run0() {
        inputReader.use { reader ->
            outputWriter.use { writer ->
                val sReads: OutputPort<VDJCAlignments> = when {
                    limit != 0L -> CountLimitingOutputPort(reader, limit)
                    else -> reader
                }
                val progress = when (sReads) {
                    is CountLimitingOutputPort -> SmartProgressReporter.extractProgress(sReads)
                    is CanReportProgress -> sReads
                    else -> throw IllegalArgumentException()
                }
                writer.inheritHeaderAndFooterFrom(reader)
                SmartProgressReporter.startProgressReport("Filtering", progress)
                var total = 0
                var passed = 0
                sReads.buffered(2048).forEach { vdjcAlignments ->
                    ++total
                    if (filter.accept(vdjcAlignments)) {
                        writer.write(vdjcAlignments)
                        ++passed
                    }
                }
                writer.setNumberOfProcessedReads(reader.numberOfReads)
                writer.setFooter(reader.footer)
                System.out.printf("%s alignments analysed\n", total)
                System.out.printf("%s alignments written (%.1f%%)\n", passed, 100.0 * passed / total)
            }
        }
    }

    private class AlignmentsFilter(
        private val containsFeature: GeneFeature?,
        private val cdr3Equals: NucleotideSequence?,
        private val chains: Chains,
        private val readsIds: TLongHashSet?,
        private val chimerasOnly: Boolean
    ) : Filter<VDJCAlignments> {
        override fun accept(`object`: VDJCAlignments): Boolean {
            if (readsIds != null && `object`.readIds.none { readId -> readId in readsIds }) return false
            val lMatch = GeneType.VDJC_REFERENCE
                .mapNotNull { `object`.getBestHit(it) }
                .any { bestHit -> chains.intersects(bestHit.gene.chains) }
            if (!lMatch) return false
            if (containsFeature != null && `object`.getFeature(containsFeature) == null) return false
            if (cdr3Equals != null) {
                val cdr3 = `object`.getFeature(GeneFeature.CDR3) ?: return false
                if (cdr3.sequence != cdr3Equals) return false
            }
            if (chimerasOnly && !`object`.isChimera) return false
            return true
        }

    }

    companion object {
        const val COMMAND_NAME = "filterAlignments"
    }
}
