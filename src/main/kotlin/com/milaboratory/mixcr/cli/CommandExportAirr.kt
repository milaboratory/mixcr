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
import cc.redberry.pipe.util.CountingOutputPort
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.withCounting
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.MiXCRFileInfo
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCObject
import com.milaboratory.mixcr.export.AirrColumns
import com.milaboratory.mixcr.export.AirrColumns.AirrAlignmentBoundary
import com.milaboratory.mixcr.export.AirrColumns.AlignmentCigar
import com.milaboratory.mixcr.export.AirrColumns.AlignmentId
import com.milaboratory.mixcr.export.AirrColumns.CloneId
import com.milaboratory.mixcr.export.AirrColumns.CompleteVDJ
import com.milaboratory.mixcr.export.AirrColumns.ComplexReferencePoint
import com.milaboratory.mixcr.export.AirrColumns.GermlineAlignment
import com.milaboratory.mixcr.export.AirrColumns.Leftmost
import com.milaboratory.mixcr.export.AirrColumns.NFeature
import com.milaboratory.mixcr.export.AirrColumns.NFeatureFromAlign
import com.milaboratory.mixcr.export.AirrColumns.NFeatureLength
import com.milaboratory.mixcr.export.AirrColumns.Productive
import com.milaboratory.mixcr.export.AirrColumns.RevComp
import com.milaboratory.mixcr.export.AirrColumns.Rightmost
import com.milaboratory.mixcr.export.AirrColumns.SequenceAlignment
import com.milaboratory.mixcr.export.AirrColumns.SequenceAlignmentBoundary
import com.milaboratory.mixcr.export.AirrColumns.VDJCCalls
import com.milaboratory.mixcr.export.AirrVDJCObjectWrapper
import com.milaboratory.mixcr.export.FieldExtractor
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.exhaustive
import com.milaboratory.util.limit
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.VDJC_REFERENCE
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.PrintStream
import java.nio.file.Path

@Command(
    name = "exportAirr",
    description = ["Exports a clns, clna or vdjca file to Airr formatted tsv file."]
)
class CommandExportAirr : MiXCRCommandWithOutputs() {
    @Option(
        description = ["Target id (use -1 to export from the target containing CDR3)."],
        names = ["-t", "--target"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_100
    )
    var targetId = -1

    @Option(
        description = ["If this option is specified, alignment fields will be padded with IMGT-style gaps."],
        names = ["-g", "--imgt-gaps"],
        order = OptionsOrder.main + 10_200
    )
    var withPadding = false

    @Option(
        description = ["Get fields like fwr1, cdr2, etc.. from alignment."],
        names = ["-a", "--from-alignment"],
        order = OptionsOrder.main + 10_300
    )
    var fromAlignment = false

    @Option(
        description = ["Limit number of filtered alignments; no more than N alignments will be outputted"],
        names = ["-n", "--limit"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_400
    )
    var limit: Int? = null

    @Parameters(
        description = ["Path to input file"],
        index = "0",
        paramLabel = "input.(vdjca|clna|clns)"
    )
    lateinit var input: Path

    @Parameters(
        description = ["Path where to write export. Will write to output if omitted."],
        index = "1",
        paramLabel = "output.tsv",
        arity = "0..1"
    )
    var out: Path? = null

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOfNotNull(out)

    private val fileType: MiXCRFileType by lazy {
        IOUtil.extractFileType(input)
    }

    private fun nFeature(gf: GeneFeature, header: String): FieldExtractor<AirrVDJCObjectWrapper> {
        require(!gf.isComposite)
        return when {
            fromAlignment -> NFeatureFromAlign(
                targetId, withPadding,
                AirrColumns.Single(gf.firstPoint), AirrColumns.Single(gf.lastPoint),
                header
            )
            else -> NFeature(gf, header)
        }
    }

    private fun commonExtractors(): List<FieldExtractor<AirrVDJCObjectWrapper>> {
        val vnpEnd: ComplexReferencePoint = Leftmost(ReferencePoint.VEndTrimmed, ReferencePoint.VEnd)
        val dnpBegin: ComplexReferencePoint = Rightmost(ReferencePoint.DBegin, ReferencePoint.DBeginTrimmed)
        val dnpEnd: ComplexReferencePoint = Leftmost(ReferencePoint.DEnd, ReferencePoint.DEndTrimmed)
        val jnpBegin: ComplexReferencePoint = Rightmost(ReferencePoint.JBegin, ReferencePoint.JBeginTrimmed)
        val np1End: ComplexReferencePoint = Leftmost(dnpBegin, jnpBegin)
        val ret = mutableListOf<FieldExtractor<AirrVDJCObjectWrapper>>()
        ret += listOf(
            AirrColumns.Sequence(targetId),
            RevComp(),
            Productive(),
            VDJCCalls(GeneType.Variable),
            VDJCCalls(GeneType.Diversity),
            VDJCCalls(GeneType.Joining),
            VDJCCalls(GeneType.Constant),
            SequenceAlignment(targetId, withPadding),
            GermlineAlignment(targetId, withPadding),
            CompleteVDJ(targetId),
            NFeature(GeneFeature.CDR3, "junction"),
            AirrColumns.AAFeature(GeneFeature.CDR3, "junction_aa"),
            NFeature(targetId, vnpEnd, np1End, "np1"),
            NFeature(targetId, dnpEnd, jnpBegin, "np2"),
            nFeature(GeneFeature.CDR1, "cdr1"),
            AirrColumns.AAFeature(GeneFeature.CDR1, "cdr1_aa"),
            nFeature(GeneFeature.CDR2, "cdr2"),
            AirrColumns.AAFeature(GeneFeature.CDR2, "cdr2_aa"),
            nFeature(GeneFeature.ShortCDR3, "cdr3"),
            AirrColumns.AAFeature(GeneFeature.ShortCDR3, "cdr3_aa"),
            nFeature(GeneFeature.FR1, "fwr1"),
            AirrColumns.AAFeature(GeneFeature.FR1, "fwr1_aa"),
            nFeature(GeneFeature.FR2, "fwr2"),
            AirrColumns.AAFeature(GeneFeature.FR2, "fwr2_aa"),
            nFeature(GeneFeature.FR3, "fwr3"),
            AirrColumns.AAFeature(GeneFeature.FR3, "fwr3_aa"),
            nFeature(GeneFeature.FR4, "fwr4"),
            AirrColumns.AAFeature(GeneFeature.FR4, "fwr4_aa"),
            AirrColumns.AlignmentScoring(targetId, GeneType.Variable),
            AlignmentCigar(targetId, GeneType.Variable),
            AirrColumns.AlignmentScoring(targetId, GeneType.Diversity),
            AlignmentCigar(targetId, GeneType.Diversity),
            AirrColumns.AlignmentScoring(targetId, GeneType.Joining),
            AlignmentCigar(targetId, GeneType.Joining),
            AirrColumns.AlignmentScoring(targetId, GeneType.Constant),
            AlignmentCigar(targetId, GeneType.Constant),
            NFeatureLength(GeneFeature.CDR3, "junction_length"),
            NFeatureLength(targetId, vnpEnd, np1End, "np1_length"),
            NFeatureLength(targetId, dnpEnd, jnpBegin, "np2_length")
        )
        for (gt in VDJC_REFERENCE) {
            for (start in booleanArrayOf(true, false)) {
                for (germline in booleanArrayOf(true, false)) {
                    ret += SequenceAlignmentBoundary(targetId, gt, start, germline)
                }
            }
        }
        for (gt in VDJC_REFERENCE) {
            for (start in booleanArrayOf(true, false)) {
                ret += AirrAlignmentBoundary(targetId, withPadding, gt, start)
            }
        }
        return ret
    }

    private fun cloneExtractors(): List<FieldExtractor<AirrVDJCObjectWrapper>> {
        val ret = mutableListOf<FieldExtractor<AirrVDJCObjectWrapper>>()
        ret += CloneId()
        ret += commonExtractors()
        ret += AirrColumns.CloneCount()
        return ret
    }

    private fun alignmentsExtractors(): List<FieldExtractor<AirrVDJCObjectWrapper>> {
        val ret = mutableListOf<FieldExtractor<AirrVDJCObjectWrapper>>()
        ret += AlignmentId()
        ret += commonExtractors()
        return ret
    }

    override fun validate() {
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.VDJCA, InputFileType.CLNX)
        }
        ValidationException.requireFileType(out, InputFileType.TSV)
    }

    override fun run1() {
        val extractors: List<FieldExtractor<AirrVDJCObjectWrapper>>
        val closeable: AutoCloseable
        var port: OutputPort<out VDJCObject>
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val cPort: CountingOutputPort<out VDJCObject>
        val fileInfo: MiXCRFileInfo
        var progressReporter: CanReportProgress? = null
        when (fileType) {
            CLNA -> {
                extractors = cloneExtractors()
                val clnaReader = ClnAReader(input, libraryRegistry, 4)
                cPort = clnaReader.readClones().withCounting()
                port = cPort
                closeable = clnaReader
                fileInfo = clnaReader
                progressReporter = SmartProgressReporter.extractProgress(cPort, clnaReader.numberOfClones().toLong())
            }
            CLNS -> {
                extractors = cloneExtractors()
                val clnsReader = ClnsReader(input, libraryRegistry)

                // I know, still writing airr is much slower...
                var maxCount = 0
                clnsReader.readClones().use { p ->
                    p.forEach {
                        ++maxCount
                    }
                }
                cPort = clnsReader.readClones().withCounting()
                port = cPort
                closeable = clnsReader
                fileInfo = clnsReader
                progressReporter = SmartProgressReporter.extractProgress(cPort, maxCount.toLong())
            }
            VDJCA -> {
                extractors = alignmentsExtractors()
                val alignmentsReader = VDJCAlignmentsReader(input, libraryRegistry)
                fileInfo = alignmentsReader
                port = alignmentsReader
                closeable = alignmentsReader
                progressReporter = alignmentsReader
            }

            SHMT -> throw UnsupportedOperationException(".shmt file unsupported")
        }.exhaustive
        if (limit != null) {
            val clop = port.limit(limit!!.toLong())
            port = clop
            progressReporter = clop
        }
        SmartProgressReporter.startProgressReport("Exporting to AIRR format", progressReporter)
        val rowMetaForExport = RowMetaForExport(
            fileInfo.header.tagsInfo,
            MetaForExport(fileInfo),
            true
        )
        (out?.let { PrintStream(it.toFile()) } ?: System.out).use { output ->
            closeable.use {
                port.use {
                    var first = true
                    for (extractor in extractors) {
                        if (!first) output.print("\t")
                        first = false
                        output.print(extractor.header)
                    }
                    output.println()
                    port.forEach { obj ->
                        first = true
                        val wrapper = AirrVDJCObjectWrapper(obj)
                        for (extractor in extractors) {
                            if (!first) output.print("\t")
                            first = false
                            output.print(extractor.extractValue(rowMetaForExport, wrapper))
                        }
                        output.println()
                    }
                }
            }
        }
    }
}
