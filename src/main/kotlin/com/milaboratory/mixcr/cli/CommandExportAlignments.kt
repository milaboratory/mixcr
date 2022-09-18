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

import cc.redberry.pipe.OutputPortCloseable
import cc.redberry.primitives.Filter
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.basictypes.*
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.OutputMode
import com.milaboratory.mixcr.export.VDJCAlignmentsFieldsExtractorsFactory
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.*
import java.nio.file.Path
import java.nio.file.Paths

object CommandExportAlignments {
    const val COMMAND_NAME = "exportAlignments"

    data class Params(
        @JsonProperty("chains") val chains: String,
        @JsonProperty("fields") val fields: List<ExportFieldDescription>,
    ) : MiXCRParams {
        override val command get() = MiXCRCommand.exportAlignments
    }

    fun Params.mkFilter(): Filter<VDJCAlignments> {
        val chains = Chains.parse(chains)
        return Filter {
            for (gt in GeneType.VJC_REFERENCE) {
                val bestHit = it.getBestHit(gt)
                if (bestHit != null && chains.intersects(bestHit.gene.chains)) return@Filter true
            }
            false
        }
    }

    abstract class CmdBase : MiXCRPresetAwareCommand<Params>() {
        @Option(
            description = ["Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated)"],
            names = ["-c", "--chains"]
        )
        private var chains: String? = null

        override val paramsResolver = object : MiXCRParamsResolver<Params>(this, MiXCRParamsBundle::exportAlignments) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::chains setIfNotNull chains
                Params::fields setIfNotEmpty VDJCAlignmentsFieldsExtractorsFactory.parsePicocli(spec.commandLine().parseResult)
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        separator = " ",
        sortOptions = false,
        description = ["Export V/D/J/C alignments into tab delimited file."]
    )
    class Cmd : CmdBase() {
        @Parameters(description = ["data.[vdjca|clns|clna]"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["table.tsv"], index = "1", arity = "0..1")
        var outputFile: Path? = null

        override fun getInputFiles(): List<String> = listOf(inputFile)

        override fun getOutputFiles(): List<String> = listOfNotNull(outputFile).map { it.toString() }

        override fun run0() {
            openAlignmentsPort(inputFile).use { data ->
                val info = data.info
                val (_, params) = paramsResolver.resolve(info.paramsSpec)

                InfoWriter.create(
                    outputFile,
                    VDJCAlignmentsFieldsExtractorsFactory.createExtractors(
                        params.fields,
                        info,
                        OutputMode.ScriptingFriendly
                    )
                ).use { writer ->
                    val reader = data.port
                    if (reader is CanReportProgress) {
                        SmartProgressReporter.startProgressReport("Exporting alignments", reader, System.err)
                    }
                    reader
                        .filter(params.mkFilter())
                        .forEach { writer.put(it) }
                }
            }
        }
    }

    data class AlignmentsAndMetaInfo(
        val port: OutputPortCloseable<VDJCAlignments>,
        val closeable: AutoCloseable,
        val info: MiXCRMetaInfo
    ) : AutoCloseable by closeable

    @JvmStatic
    fun openAlignmentsPort(inputFile: String): AlignmentsAndMetaInfo =
        when (IOUtil.extractFileType(Paths.get(inputFile))) {
            IOUtil.MiXCRFileType.VDJCA -> {
                val vdjcaReader = VDJCAlignmentsReader(inputFile, VDJCLibraryRegistry.getDefault())
                AlignmentsAndMetaInfo(vdjcaReader, vdjcaReader, vdjcaReader.info)
            }

            IOUtil.MiXCRFileType.CLNA -> {
                val clnaReader = ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4))
                val source = clnaReader.readAllAlignments()
                val port = object : OutputPortCloseable<VDJCAlignments> {
                    override fun close() {
                        source.close()
                        clnaReader.close()
                    }

                    override fun take(): VDJCAlignments? = source.take()
                }
                AlignmentsAndMetaInfo(port, clnaReader, clnaReader.info)
            }

            IOUtil.MiXCRFileType.CLNS -> throw RuntimeException("Can't export alignments from *.clns file: $inputFile")
            IOUtil.MiXCRFileType.SHMT -> throw RuntimeException("Can't export alignments from *.shmt file: $inputFile")
        }.exhaustive

    @JvmStatic
    fun mkSpec(): Model.CommandSpec {
        val cmd = Cmd()
        val spec = Model.CommandSpec.forAnnotatedObject(cmd)
        cmd.spec = spec // inject spec manually
        VDJCAlignmentsFieldsExtractorsFactory.addOptionsToSpec(spec)
        return spec
    }
}