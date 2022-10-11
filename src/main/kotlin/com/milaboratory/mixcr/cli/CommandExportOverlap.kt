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

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.FieldExtractor
import com.milaboratory.mixcr.export.OutputMode
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil
import com.milaboratory.mixcr.postanalysis.preproc.ChainsFilter
import com.milaboratory.mixcr.postanalysis.util.OverlapBrowser
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Model
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.collections.component1
import kotlin.collections.component2

@Command(
    separator = " ",
    description = ["Build cloneset overlap and export into tab delimited file."],
    sortOptions = false
)
class CommandExportOverlap : AbstractMiXCRCommand() {
    @Parameters(description = ["cloneset.{clns|clna}... output.tsv"])
    var inOut: List<String> = mutableListOf()

    @Option(description = ["Chains to export"], names = ["--chains"], split = ",")
    var chains: Set<String>? = null

    @Option(
        description = ["Filter out-of-frame sequences and clonotypes with stop-codons"],
        names = ["--only-productive"]
    )
    var onlyProductive = false

    @Option(description = ["Overlap criteria. Default CDR3|AA|V|J"], names = ["--criteria"])
    var overlapCriteria = "CDR3|AA|V|J"

    public override val inputFiles: List<String>
        get() = inOut.subList(0, inOut.size - 1)

    override val outputFiles: List<String>
        get() = listOf(inOut.last())

    private fun getOut(chains: Chains): Path {
        val out = Paths.get(inOut.last()).toAbsolutePath()
        var fName = out.fileName.toString()
        fName = when {
            fName.endsWith(".tsv") -> "${fName.replace("tsv", "")}${chains}.tsv"
            else -> "${fName}_${chains}"
        }
        return out.parent.resolve(fName)
    }

    override fun run0() {
        val samples = inputFiles
        val chains = this.chains?.let { ChainsFilter.parseChainsList(this.chains) }
        val criteria = OverlapUtil.parseCriteria(overlapCriteria)
        val extractors = mutableListOf<OverlapFieldExtractor>()
        extractors += ExtractorUnique(
            object : FieldExtractor<Clone> {
                override val header = (if (criteria.isAA) "aaSeq" else "nSeq") + GeneFeature.encode(criteria.feature)

                override fun extractValue(obj: Clone) = when {
                    criteria.isAA -> obj.getAAFeature(criteria.feature).toString()
                    else -> obj.getNFeature(criteria.feature).toString()
                }
            }
        )
        if (criteria.withV) {
            extractors += ExtractorUnique(
                object : FieldExtractor<Clone> {
                    override val header = "vGene"

                    override fun extractValue(obj: Clone) = obj.getBestHit(Variable).gene.name
                }
            )
        }
        if (criteria.withJ) {
            extractors += ExtractorUnique(
                object : FieldExtractor<Clone> {
                    override val header: String = "jGene"

                    override fun extractValue(obj: Clone) = obj.getBestHit(Joining).gene.name
                }
            )
        }
        extractors += NumberOfSamples()
        extractors += TotalCount()
        extractors += TotalFraction()
        val fieldExtractors: List<FieldExtractor<Clone>> =
            CloneSetIO.mkReader(Paths.get(samples[0]), VDJCLibraryRegistry.getDefault()).use { cReader ->
                CloneFieldsExtractorsFactory.createExtractors(
                    CloneFieldsExtractorsFactory
                        .parsePicocli(spec.commandLine().parseResult), cReader.header, OutputMode.ScriptingFriendly
                )
            }
        extractors += fieldExtractors.map { ExtractorPerSample(it) }

        val overlapBrowser = OverlapBrowser(onlyProductive)
        SmartProgressReporter.startProgressReport(overlapBrowser)

        val countsByChain = overlapBrowser.computeCountsByChain(samples)
        val chainsToWrite = chains?.intersect(countsByChain.keys) ?: countsByChain.keys
        val writers = chainsToWrite.associateWith { chain ->
            val writer = InfoWriter(getOut(chain), samples, extractors)
            writer.writeHeader()
            writer
        }

        val overlap = OverlapUtil.overlap(samples, { true }, criteria.ordering())
        overlap.mkElementsPort().use { port ->
            overlapBrowser.overlap(countsByChain, port).forEach { row ->
                for ((chains, cloneOverlapGroup) in row) {
                    writers[chains]!!.writeRow(cloneOverlapGroup)
                }
            }
        }
        writers.values.forEach { it.close() }
    }

    private class InfoWriter(
        out: Path,
        samples: List<String>,
        private val extractors: List<OverlapFieldExtractor>
    ) : AutoCloseable {
        private val writer: PrintWriter = PrintWriter(FileWriter(out.toFile()))
        private val samplesNames = samples.map { sampleName -> removeExt(sampleName) }

        fun writeHeader() {
            writer.println(extractors
                .flatMap { fieldExtractor -> fieldExtractor.header(samplesNames) }
                .joinToString("\t")
            )
        }

        fun writeRow(row: OverlapGroup<Clone>) {
            writer.println(extractors
                .flatMap { fieldExtractor -> fieldExtractor.values(row) }
                .joinToString("\t")
            )
        }

        override fun close() {
            writer.close()
        }

        companion object {
            private fun removeExt(sampleName: String): String = sampleName
                .replace(".clns", "")
                .replace(".clna", "")
        }
    }

    private interface OverlapFieldExtractor {
        fun header(samples: List<String>): List<String>
        fun values(row: OverlapGroup<Clone>): List<String>
    }

    private class TotalCount : OverlapFieldExtractor {
        override fun header(samples: List<String>): List<String> =
            samples.map { sample -> "${sample}_countAggregated" }

        override fun values(row: OverlapGroup<Clone>): List<String> =
            row.map { clones -> clones.sumOf { it.count }.toString() }
    }

    private class TotalFraction : OverlapFieldExtractor {
        override fun header(samples: List<String>): List<String> =
            samples.map { sample -> "${sample}_fractionAggregated" }

        override fun values(row: OverlapGroup<Clone>): List<String> =
            row.map { clones -> clones.sumOf { it.fraction }.toString() }
    }

    private class NumberOfSamples : OverlapFieldExtractor {
        override fun header(samples: List<String>): List<String> = listOf("nSamples")

        override fun values(row: OverlapGroup<Clone>): List<String> =
            listOf(row.count { clones -> clones.isNotEmpty() }.toString())
    }

    private class ExtractorUnique(
        private val extractor: FieldExtractor<Clone>
    ) : OverlapFieldExtractor {
        override fun header(samples: List<String>): List<String> = listOf(extractor.header)

        override fun values(row: OverlapGroup<Clone>): List<String> =
            listOf(extractor.extractValue(row.first { clones -> clones.isNotEmpty() }.first()))
    }

    private class ExtractorPerSample(val extractor: FieldExtractor<Clone>) : OverlapFieldExtractor {
        override fun header(samples: List<String>): List<String> =
            samples.map { sample -> sample + "_" + extractor.header }

        override fun values(row: OverlapGroup<Clone>): List<String> =
            row.map { clones ->
                clones.joinToString(",") { clone -> extractor.extractValue(clone) }
            }
    }

    companion object {
        @JvmStatic
        fun mkSpec(): Model.CommandSpec {
            val export = CommandExportOverlap()
            val spec = Model.CommandSpec.forAnnotatedObject(export)
            export.spec = spec // inject spec manually
            CloneFieldsExtractorsFactory.addOptionsToSpec(spec)
            return spec
        }
    }
}
