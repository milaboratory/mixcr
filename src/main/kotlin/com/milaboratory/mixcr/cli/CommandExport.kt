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
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.basictypes.*
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.*
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.export.*
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.CanReportProgressAndStage
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Stream
import kotlin.reflect.KProperty1

object CommandExport {

    data class Params(
        @JsonProperty("splitByTags") val splitByTags: String?,
        @JsonProperty("filterOutOfFrames") val filterOutOfFrames: Boolean,
        @JsonProperty("filterStops") val filterStops: Boolean,
        @JsonProperty("chains") val chains: String,
        @JsonProperty("fields") val fields: List<ExportFieldDescription>,
    )

    private interface ManualSpec {
        fun addOptionsToSpec(spec: CommandSpec)
        var spec: CommandSpec
    }

    fun <T : VDJCObject> mkFilter(params: Params): Filter<T> {
        // FIXME !! the following is very bad
        val chains = Chains.parse(params.chains)
        return Filter { vdjcObject: T ->
            if (params.filterOutOfFrames) {
                if (vdjcObject.isOutOfFrameOrAbsent(GeneFeature.CDR3)) return@Filter false
            }
            if (params.filterStops) {
                if (vdjcObject is Clone)
                    for (assemblingFeature in vdjcObject.parentCloneSet.assemblingFeatures) {
                        if (vdjcObject.containsStopsOrAbsent(assemblingFeature)) return@Filter false
                    }
                else
                    if (vdjcObject.containsStopsOrAbsent(GeneFeature.CDR3)) return@Filter false
            }
            for (gt in GeneType.VJC_REFERENCE) {
                val bestHit = vdjcObject.getBestHit(gt)
                if (bestHit != null && chains.intersects(bestHit.gene.chains)) return@Filter true
            }
            false
        }
    }

    abstract class CmdBase<T : VDJCObject>(
        protected val fieldExtractorsFactory: FieldExtractorsFactoryNew<T>,
        paramsProperty: KProperty1<MiXCRParamsBundle, Params?>,
    ) : MiXCRPresetAwareCommand<Params>(), ManualSpec {
        @Option(
            description = ["Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated)"],
            names = ["-c", "--chains"]
        )
        private var chains: String? = null

        @Option(
            description = ["Exclude clones with out-of-frame clone sequences (fractions will be recalculated)"],
            names = ["-o", "--filter-out-of-frames"]
        )
        private var filterOutOfFrames = false

        @Option(
            description = ["Exclude sequences containing stop codons (fractions will be recalculated)"],
            names = ["-t", "--filter-stops"]
        )
        private var filterStops = false

        // FIXME implement for alignments
        @Option(description = ["Split clones by tag values"], names = ["--split-by-tag"])
        private var splitByTag: String? = null

        override fun addOptionsToSpec(spec: CommandSpec) {
            fieldExtractorsFactory.addOptionsToSpec(spec)
        }

        override val paramsResolver = object : MiXCRParamsResolver<Params>(paramsProperty) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::chains setIfNotNull chains
                Params::filterOutOfFrames setIfTrue filterOutOfFrames
                Params::filterStops setIfTrue filterStops
                Params::splitByTags setIfNotNull splitByTag
                Params::fields setIfNotEmpty fieldExtractorsFactory.parsePicocli(spec.commandLine().parseResult)
            }
        }

        @Parameters(description = ["data.[vdjca|clns|clna]"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["table.tsv"], index = "1", arity = "0..1")
        var outputFile: Path? = null

        // @Option(description = ["Output only first N records"], names = ["-n", "--limit"])
        // var limit = Long.MAX_VALUE
        //     set(value) {
        //         if (value <= 0) throwExecutionExceptionKotlin("--limit must be positive")
        //         field = value
        //     }

        override fun getInputFiles(): List<String> = listOf(inputFile)

        override fun getOutputFiles(): List<String> = listOfNotNull(outputFile).map { it.toString() }

        override var spec: CommandSpec
            get() = this@CmdBase.spec
            set(value) {
                this@CmdBase.spec = value
            }
    }

    private fun <T : ManualSpec> mkCommandSpec(export: T): CommandSpec {
        val spec = CommandSpec.forAnnotatedObject(export)
        export.spec = spec // inject spec manually
        export.addOptionsToSpec(spec)
        return spec
    }

    /**
     * Creates command spec for given type VDJAlignments
     */
    @JvmStatic
    fun mkAlignmentsSpec(): CommandSpec = mkCommandSpec(CommandExportAlignments())

    /**
     * Creates command spec for given type VDJAlignments
     */
    @JvmStatic
    fun mkClonesSpec(): CommandSpec = mkCommandSpec(CommandExportClones())

    @Command(
        name = "exportAlignments",
        separator = " ",
        sortOptions = false,
        description = ["Export V/D/J/C alignments into tab delimited file."]
    )
    class CommandExportAlignments : CmdBase<VDJCAlignments>(
        VDJCAlignmentsFieldsExtractorsFactory,
        MiXCRParamsBundle::exportAlignments,
    ) {
        override fun run0() {
            openAlignmentsPort(inputFile).use { data ->
                val info = data.info
                val (_, params) = paramsResolver.parse(info.paramsBundle)

                InfoWriter.create(
                    outputFile,
                    fieldExtractorsFactory.createExtractors(params.fields, info, OutputMode.ScriptingFriendly)
                ).use { writer ->
                    val reader = data.port
                    if (reader is CanReportProgress) {
                        SmartProgressReporter.startProgressReport("Exporting alignments", reader, System.err)
                    }
                    val filter = mkFilter<VDJCAlignments>(params)
                    reader
                        .filter(filter)
                        .forEach { writer.put(it) }
                }
            }
        }
    }

    @Command(
        name = "exportClones",
        separator = " ",
        sortOptions = false,
        description = ["Export assembled clones into tab delimited file."]
    )
    class CommandExportClones : CmdBase<Clone>(
        CloneFieldsExtractorsFactory,
        MiXCRParamsBundle::exportAlignments,
    ) {
        override fun run0() {
            val initialSet = CloneSetIO.read(inputFile, VDJCLibraryRegistry.getDefault())
            val info = initialSet.info
            val (_, params) = paramsResolver.parse(info.paramsBundle)
            InfoWriter.create(
                outputFile,
                fieldExtractorsFactory.createExtractors(params.fields, info, OutputMode.ScriptingFriendly)
            ).use { writer ->
                val set = CloneSet.transform(initialSet, mkFilter(params))
                val tagsInfo = set.tagsInfo
                val exportClones = ExportClones(
                    set, writer, Long.MAX_VALUE,
                    if (params.splitByTags == null) 0 else tagsInfo.indexOf(params.splitByTags) + 1
                )
                SmartProgressReporter.startProgressReport(exportClones, System.err)
                exportClones.run()
                if (initialSet.size() > set.size()) {
                    val initialCount = initialSet.clones.stream().mapToDouble { obj: Clone -> obj.count }
                        .sum()
                    val count = set.clones.stream().mapToDouble { obj: Clone -> obj.count }
                        .sum()
                    val di = initialSet.size() - set.size()
                    val cdi = initialCount - count
                    val percentageDI = ReportHelper.PERCENT_FORMAT.format(100.0 * di / initialSet.size())
                    warn(
                        "Filtered ${set.size()} of ${initialSet.size()} clones ($percentageDI%)."
                    )
                    val percentageCDI = ReportHelper.PERCENT_FORMAT.format(100.0 * cdi / initialCount)
                    warn(
                        "Filtered $count of $initialCount reads ($percentageCDI%)."
                    )
                }
            }
        }

        class ExportClones(
            val clones: CloneSet,
            val writer: InfoWriter<Clone>,
            val limit: Long,
            private val splitByLevel: Int
        ) : CanReportProgressAndStage {
            val size: Long = clones.size().toLong()

            @Volatile
            private var current: Long = 0

            override fun getStage(): String = "Exporting clones"

            override fun getProgress(): Double = 1.0 * current / size

            override fun isFinished(): Boolean = current == size

            fun run() {
                var currentLocal = current
                for (clone in clones.clones) {
                    if (currentLocal == limit) break
                    var stream = Stream.of(clone)
                    if (splitByLevel > 0) {
                        stream = stream.flatMap { cl: Clone ->
                            val tagCount = cl.tagCount
                            val sum = tagCount.sum()
                            Arrays.stream(tagCount.splitBy(splitByLevel))
                                .map { tc: TagCount ->
                                    Clone(
                                        clone.targets, clone.hits,
                                        tc, 1.0 * cl.count * tc.sum() / sum, clone.id, clone.group
                                    )
                                }
                        }
                    }
                    stream.forEach { t: Clone -> writer.put(t) }
                    ++currentLocal
                    current = currentLocal
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
            VDJCA -> {
                val vdjcaReader = VDJCAlignmentsReader(inputFile, VDJCLibraryRegistry.getDefault())
                AlignmentsAndMetaInfo(vdjcaReader, vdjcaReader, vdjcaReader.info)
            }

            CLNA -> {
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

            CLNS -> throw RuntimeException("Can't export alignments from *.clns file: $inputFile")
            SHMT -> throw RuntimeException("Can't export alignments from *.shmt file: $inputFile")
        }.exhaustive
}
