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
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.OutputMode
import com.milaboratory.util.CanReportProgressAndStage
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Model
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.util.*
import java.util.stream.Stream

object CommandExportClones {
    const val COMMAND_NAME = "exportClones"

    data class Params(
        @JsonProperty("splitByTags") val splitByTags: String?,
        @JsonProperty("filterOutOfFrames") val filterOutOfFrames: Boolean,
        @JsonProperty("filterStops") val filterStops: Boolean,
        @JsonProperty("chains") val chains: String,
        @JsonProperty("noHeader") val noHeader: Boolean,
        @JsonProperty("fields") val fields: List<ExportFieldDescription>,
    ) : MiXCRParams {
        override val command get() = MiXCRCommand.exportClones
    }

    fun Params.mkFilter(): Filter<Clone> {
        val chains = Chains.parse(chains)
        return Filter {
            if (filterOutOfFrames)
                if (it.isOutOfFrameOrAbsent(GeneFeature.CDR3)) return@Filter false

            if (filterStops)
                for (assemblingFeature in it.parentCloneSet.assemblingFeatures)
                    if (it.containsStopsOrAbsent(assemblingFeature)) return@Filter false

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

        @Option(
            description = ["Don't print first header line, print only data"],
            names = ["--no-header"]
        )
        private var noHeader = false

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

        @Option(description = ["Split clones by tag values"], names = ["--split-by-tag"])
        private var splitByTag: String? = null

        override val paramsResolver = object : MiXCRParamsResolver<Params>(this, MiXCRParamsBundle::exportClones) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::chains setIfNotNull chains
                Params::filterOutOfFrames setIfTrue filterOutOfFrames
                Params::filterStops setIfTrue filterStops
                Params::splitByTags setIfNotNull splitByTag
                Params::noHeader setIfTrue noHeader
                Params::fields setIfNotEmpty CloneFieldsExtractorsFactory.parsePicocli(spec.commandLine().parseResult)
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        separator = " ",
        sortOptions = false,
        description = ["Export assembled clones into tab delimited file."]
    )
    class Cmd : CmdBase() {
        @Parameters(description = ["data.[vdjca|clns|clna]"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["table.tsv"], index = "1", arity = "0..1")
        var outputFile: Path? = null

        override fun getInputFiles(): List<String> = listOf(inputFile)

        override fun getOutputFiles(): List<String> = listOfNotNull(outputFile).map { it.toString() }

        override fun run0() {
            val initialSet = CloneSetIO.read(inputFile, VDJCLibraryRegistry.getDefault())
            val header = initialSet.header
            val tagsInfo = header.tagsInfo
            val (_, params) = paramsResolver.resolve(
                header.paramsSpec,
                printParameters = outputFile != null
            ) { params ->
                if (params.splitByTags == null) {
                    val newSpitBy = params.fields
                        .filter { it.field.equals("-tag", ignoreCase = true) }
                        .map { it.args[0] to tagsInfo.indexOf(it.args[0]) }
                        .maxByOrNull { it.second }
                        ?.first
                    if (newSpitBy != null) {
                        println("Clone splitting by $newSpitBy added automatically because -tag $newSpitBy field is present in the list.")
                        params.copy(splitByTags = newSpitBy)
                    } else
                        params
                } else
                    params
            }
            InfoWriter.create(
                outputFile,
                CloneFieldsExtractorsFactory.createExtractors(params.fields, header, OutputMode.ScriptingFriendly),
                !params.noHeader
            ).use { writer ->
                val set = CloneSet.transform(initialSet, params.mkFilter())
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

    @JvmStatic
    fun mkSpec(): Model.CommandSpec {
        val cmd = Cmd()
        val spec = Model.CommandSpec.forAnnotatedObject(cmd)
        cmd.spec = spec // inject spec manually
        CloneFieldsExtractorsFactory.addOptionsToSpec(spec)
        return spec
    }
}
