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
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.export.ExportDefaultOptions
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
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

object CommandExportAlignments {
    const val COMMAND_NAME = "exportAlignments"

    data class Params(
        @JsonProperty("chains") val chains: String,
        @JsonProperty("noHeader") val noHeader: Boolean,
        @JsonProperty("fields") val fields: List<ExportFieldDescription>,
    ) : MiXCRParams {
        override val command get() = MiXCRCommandDescriptor.exportAlignments
    }

    fun Params.mkFilter(): Filter<VDJCAlignments> {
        return Filter {
            for (gt in GeneType.VJC_REFERENCE) {
                val bestHit = it.getBestHit(gt)
                if (bestHit != null && Chains.parse(chains).intersects(bestHit.gene.chains)) return@Filter true
            }
            false
        }
    }

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Params> {
        @Option(
            description = [
                "Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated)",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-c", "--chains"],
            paramLabel = Labels.CHAINS
        )
        private var chains: String? = null

        @Mixin
        private lateinit var exportDefaults: ExportDefaultOptions

        override val paramsResolver = object : MiXCRParamsResolver<Params>(MiXCRParamsBundle::exportAlignments) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::chains setIfNotNull chains
                Params::noHeader setIfTrue exportDefaults.noHeader
                Params::fields updateBy exportDefaults.fieldsUpdater(VDJCAlignmentsFieldsExtractorsFactory)
            }
        }
    }

    @Command(
        description = ["Export V/D/J/C alignments into tab delimited file."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file"],
            paramLabel = "data.(vdjca|clna)",
            index = "0"
        )
        lateinit var inputFile: Path

        @set:Parameters(
            description = ["Path where to write export table. Will write to output if omitted."],
            paramLabel = "table.tsv",
            index = "1",
            arity = "0..1"
        )
        var outputFile: Path? = null
            set(value) {
                ValidationException.requireTSV(value)
                field = value
            }

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOfNotNull(outputFile)

        override fun run0() {
            openAlignmentsPort(inputFile).use { data ->
                val info = data.info
                val (_, params) = paramsResolver.resolve(info.paramsSpec, printParameters = outputFile != null)

                InfoWriter.create(
                    outputFile,
                    VDJCAlignmentsFieldsExtractorsFactory.createExtractors(
                        params.fields,
                        info,
                        OutputMode.ScriptingFriendly
                    ),
                    !params.noHeader
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
        val info: MiXCRHeader
    ) : AutoCloseable by closeable

    @JvmStatic
    fun openAlignmentsPort(inputFile: Path): AlignmentsAndMetaInfo =
        when (IOUtil.extractFileType(inputFile)) {
            IOUtil.MiXCRFileType.VDJCA -> {
                val vdjcaReader = VDJCAlignmentsReader(
                    inputFile,
                    VDJCLibraryRegistry.getDefault()
                )
                AlignmentsAndMetaInfo(vdjcaReader, vdjcaReader, vdjcaReader.header)
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
                AlignmentsAndMetaInfo(port, clnaReader, clnaReader.header)
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
