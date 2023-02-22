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

import cc.redberry.pipe.util.buffered
import cc.redberry.pipe.util.forEach
import cc.redberry.primitives.Filter
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.limit
import gnu.trove.set.hash.TLongHashSet
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    description = ["Filter alignments."]
)
class CommandFilterAlignments : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Path to input file with alignments."],
        paramLabel = "alignments.vdjca",
        index = "0"
    )
    lateinit var input: Path

    @Parameters(
        description = ["Path where to write filtered alignments."],
        paramLabel = "alignments.filtered.vdjca",
        index = "1"
    )
    lateinit var out: Path

    @Option(
        description = ["Specifies immunological protein chain gene for an alignment. If many, " +
                "separated by ','. Available genes: IGH, IGL, IGK, TRA, TRB, TRG, TRD."],
        names = ["-c", "--chains"],
        paramLabel = Labels.CHAINS,
        showDefaultValue = ALWAYS,
        order = OptionsOrder.main + 10_100,
        completionCandidates = ChainsCandidates::class
    )
    var chains: Chains = Chains.ALL

    @Option(
        description = ["Include only those alignments that contain specified feature."],
        names = ["-g", "--contains-feature"],
        paramLabel = Labels.GENE_FEATURE,
        order = OptionsOrder.main + 10_200,
        completionCandidates = GeneFeaturesCandidates::class
    )
    var containsFeature: GeneFeature? = null

    @Option(
        description = ["Include only those alignments which CDR3 equals to a specified sequence."],
        names = ["-e", "--cdr3-equals"],
        paramLabel = "<seq>",
        order = OptionsOrder.main + 10_300
    )
    var cdr3Equals: NucleotideSequence? = null

    @Option(
        description = ["Output only chimeric alignments."],
        names = ["-x", "--chimeras-only"],
        order = OptionsOrder.main + 10_400
    )
    var chimerasOnly = false

    @set:Option(
        description = ["Maximal number of reads to process"],
        names = ["-n", "--limit"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_500
    )
    var limit: Long = 0
        set(value) {
            if (value <= 0) throw ValidationException("-n / --limit must be positive.")
            field = value
        }

    @set:Option(
        description = ["List of read ids to export"],
        names = ["-i", "--read-ids"],
        hidden = true
    )
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

    private val filter: AlignmentsFilter by lazy {
        AlignmentsFilter(
            containsFeature, cdr3Equals, chains,
            readIds, chimerasOnly
        )
    }

    override fun validate() {
        ValidationException.requireFileType(input, InputFileType.VDJCA)
        ValidationException.requireFileType(out, InputFileType.VDJCA)
    }

    override fun run1() {
        inputReader.use { reader ->
            outputWriter.use { writer ->
                val sReads = when {
                    limit != 0L -> reader.limit(limit)
                    else -> reader
                }
                writer.inheritHeaderAndFooterFrom(reader)
                SmartProgressReporter.startProgressReport("Filtering", sReads)
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
        override fun accept(obj: VDJCAlignments): Boolean {
            if (readsIds != null && obj.readIds.none { readId -> readId in readsIds }) return false
            val lMatch = GeneType.VDJC_REFERENCE
                .mapNotNull { obj.getBestHit(it) }
                .any { bestHit -> chains.intersects(bestHit.gene.chains) }
            if (!lMatch) return false
            if (containsFeature != null && obj.getFeature(containsFeature) == null) return false
            if (cdr3Equals != null) {
                val cdr3 = obj.getFeature(GeneFeature.CDR3) ?: return false
                if (cdr3.sequence != cdr3Equals) return false
            }
            if (chimerasOnly && !obj.isChimera) return false
            return true
        }

    }

}
