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
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mixcr.basictypes.*
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.*
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.export.*
import com.milaboratory.mixcr.postanalysis.preproc.ChainsFilter
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.mixcr.util.and
import com.milaboratory.primitivio.asFilter
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.limit
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.CanReportProgressAndStage
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Stream

@CommandLine.Command(separator = " ")
abstract class CommandExport<T : VDJCObject> private constructor(
    protected val fieldExtractorsFactory: FieldExtractorsFactory<T>
) : MiXCRCommand() {
    @CommandLine.Parameters(description = ["data.[vdjca|clns|clna]"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["table.tsv"], index = "1", arity = "0..1")
    var out: Path? = null

    @CommandLine.Option(
        description = ["Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated). " +
                "Possible values (multiple values allowed): TRA, TRD, TRAD (for human), TRG, IGH, IGK, IGL"],
        names = ["-c", "--chains"],
        split = ","
    )
    var chains: Set<String>? = null

    @CommandLine.Option(description = ["List available export fields"], names = ["-lf", "--list-fields"], hidden = true)
    fun setListFields(@Suppress("UNUSED_PARAMETER") b: Boolean) {
        throwExecutionExceptionKotlin("-lf / --list-fields is removed in version 3.0: use help <exportCommand> for help")
    }

    @CommandLine.Option(
        description = ["Output short versions of column headers which facilitates analysis with Pandas, R/DataFrames or other data tables processing library."],
        names = ["-s", "--no-spaces"],
        hidden = true
    )
    fun setNoSpaces(@Suppress("UNUSED_PARAMETER") b: Boolean) {
        warn(
            """"-s" / "--no-spaces" option is deprecated.
Scripting friendly output format now used by default.
Use "-v" / "--with-spaces" to switch back to human readable format.""".trimIndent()
        )
    }

    @CommandLine.Option(description = ["Output only first N records"], names = ["-n", "--limit"])
    var limit = Long.MAX_VALUE
        set(value) {
            if (value <= 0) throwExecutionExceptionKotlin("--limit must be positive")
            field = value
        }

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOfNotNull(out).map { it.toString() }

    open fun mkFilter(): Filter<T> {
        if (chains == null)
            return Filter { true }

        return ChainsFilter.parseFilter<T>(chains).asFilter()
    }

    @CommandLine.Command(
        name = "exportAlignments",
        separator = " ",
        sortOptions = false,
        description = ["Export V/D/J/C alignments into tab delimited file."]
    )
    class CommandExportAlignments : CommandExport<VDJCAlignments>(VDJCAlignmentsFieldsExtractorsFactory) {
        override fun run0() {
            openAlignmentsPort(`in`).use { readerAndHeader ->
                InfoWriter.create(
                    out,
                    fieldExtractorsFactory,
                    spec.commandLine().parseResult,
                    readerAndHeader.header
                ).use { writer ->
                    val reader = readerAndHeader.port
                    if (reader is CanReportProgress) {
                        SmartProgressReporter.startProgressReport("Exporting alignments", reader, System.err)
                    }
                    val filter = mkFilter()
                    reader
                        .filter(filter)
                        .limit(limit)
                        .forEach { writer.put(it) }
                }
            }
        }
    }

    @CommandLine.Command(
        name = "exportClones",
        separator = " ",
        sortOptions = false,
        description = ["Export assembled clones into tab delimited file."]
    )
    class CommandExportClones : CommandExport<Clone>(CloneFieldsExtractorsFactory) {
        @Option(
            description = ["Exclude clones with out-of-frame clone sequences (fractions will be recalculated)"],
            names = ["-o", "--filter-out-of-frames"]
        )
        var filterOutOfFrames = false

        @Option(
            description = ["Exclude sequences containing stop codons (fractions will be recalculated)"],
            names = ["-t", "--filter-stops"]
        )
        var filterStops = false

        @Option(
            description = ["Filter clones by minimal clone fraction"],
            names = ["-q", "--minimal-clone-fraction"]
        )
        var minFraction = 0f

        @Option(
            description = ["Filter clones by minimal clone read count"],
            names = ["-m", "--minimal-clone-count"]
        )
        var minCount: Long = 0

        @Option(description = ["Split clones by tag values"], names = ["--split-by-tag"])
        var splitByTag: String? = null

        override fun mkFilter(): Filter<Clone> = super.mkFilter().and(CFilter(filterOutOfFrames, filterStops))

        override fun run0() {
            val initialSet = CloneSetIO.read(`in`, VDJCLibraryRegistry.getDefault())
            InfoWriter.create(out, fieldExtractorsFactory, spec.commandLine().parseResult, initialSet).use { writer ->
                val set = CloneSet.transform(initialSet, mkFilter())
                for (i in 0 until set.size()) {
                    if (set[i].fraction < minFraction ||
                        set[i].count < minCount
                    ) {
                        limit = i.toLong()
                        break
                    }
                }
                val tagsInfo = set.tagsInfo
                val exportClones = ExportClones(
                    set, writer, limit,
                    if (splitByTag == null) 0 else tagsInfo.indexOf(splitByTag) + 1
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

        class CFilter(val filterOutOfFrames: Boolean, val filterStopCodons: Boolean) : Filter<Clone> {
            override fun accept(clone: Clone): Boolean {
                if (filterOutOfFrames) {
                    if (clone.isOutOfFrameOrAbsent(GeneFeature.CDR3)) return false
                }
                if (filterStopCodons) {
                    for (assemblingFeature in clone.parentCloneSet.assemblingFeatures) {
                        if (clone.containsStopsOrAbsent(assemblingFeature)) return false
                    }
                }
                return true
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is CFilter) return false
                return filterOutOfFrames == other.filterOutOfFrames &&
                        filterStopCodons == other.filterStopCodons
            }

            override fun hashCode(): Int {
                return Objects.hash(filterOutOfFrames, filterStopCodons)
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

    class AlignmentsAndHeader(
        val port: OutputPortCloseable<VDJCAlignments>, val header: VDJCFileHeaderData
    ) : AutoCloseable by port

    companion object {
        /**
         * Creates command spec for given type (Clone / VDJAlignments)
         */
        private fun <T : VDJCObject> mkCommandSpec(export: CommandExport<T>): CommandLine.Model.CommandSpec {
            val spec = CommandLine.Model.CommandSpec.forAnnotatedObject(export)
            export.spec = spec // inject spec manually
            export.fieldExtractorsFactory.addOptionsToSpec(spec, true)
            return spec
        }

        /**
         * Creates command spec for given type VDJAlignments
         */
        @JvmStatic
        fun mkAlignmentsSpec(): CommandLine.Model.CommandSpec = mkCommandSpec(CommandExportAlignments())

        /**
         * Creates command spec for given type VDJAlignments
         */
        @JvmStatic
        fun mkClonesSpec(): CommandLine.Model.CommandSpec = mkCommandSpec(CommandExportClones())

        @JvmStatic
        fun openAlignmentsPort(`in`: String): AlignmentsAndHeader =
            when (IOUtil.extractFileType(Paths.get(`in`))) {
                VDJCA -> {
                    val vdjcaReader = VDJCAlignmentsReader(`in`, VDJCLibraryRegistry.getDefault())
                    AlignmentsAndHeader(vdjcaReader, vdjcaReader)
                }
                CLNA -> {
                    val clnaReader = ClnAReader(`in`, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4))
                    val source = clnaReader.readAllAlignments()
                    val port = object : OutputPortCloseable<VDJCAlignments> {
                        override fun close() {
                            source.close()
                            clnaReader.close()
                        }

                        override fun take(): VDJCAlignments? = source.take()
                    }
                    AlignmentsAndHeader(port, clnaReader)
                }
                CLNS -> throw RuntimeException("Can't export alignments from *.clns file: $`in`")
                SHMT -> throw RuntimeException("Can't export alignments from *.shmt file: $`in`")
            }.exhaustive
    }
}