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
package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.allReports
import com.milaboratory.mixcr.alleles.FindAllelesReport
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport
import com.milaboratory.mixcr.cli.AlignerReport
import com.milaboratory.mixcr.cli.CloneAssemblerReport
import com.milaboratory.mixcr.cli.CommandExportReportsAsTable
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.export.ReportFieldsExtractors.ReportsWithSource
import com.milaboratory.mixcr.trees.BuildSHMTreeReport
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import com.milaboratory.util.ReportHelper
import picocli.CommandLine

private fun Double.formatPercentage(): String = ReportHelper.PERCENT_FORMAT.format(this * 100)

private fun asPercentage(value: Long, base: Long) = (value / base.toDouble()).formatPercentage()

object ReportFieldsExtractors : FieldExtractorsFactoryWithPresets<ReportsWithSource>() {
    override val presets: Map<String, List<ExportFieldDescription>> = buildMap {
        this["min"] = listOf(
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-MiXCRVersion"),
            ExportFieldDescription("-totalReads"),
            ExportFieldDescription("-successAligned"),
            ExportFieldDescription("-totalClonotypes"),
            ExportFieldDescription("-readsUsedInClonotypes"),
        )
        this["full"] = allAvailableFields()
            .sortedBy { it.priority }
            .filter { it.deprecation == null }
            .onEach { check(it.arity == CommandLine.Range.valueOf("0")) }
            .map { it.cmdArgName }
            .map { ExportFieldDescription(it) }
    }
    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<FieldsCollection<ReportsWithSource>> = buildList {
        this += Field(
            100,
            "-fileName",
            "File name as it was specified in command `${CommandExportReportsAsTable.COMMAND_NAME}`.",
            "fileName"
        ) { reportsWithSource ->
            reportsWithSource.source
        }
        this += Field(
            200,
            "-MiXCRVersion",
            "Version of MiXCR.",
            "MiXCRVersion"
        ) { reportsWithSource ->
            reportsWithSource.reports.map { it.version }.distinct().joinToString(",")
        }
        this += Field(
            100_000,
            "-inputFilesAlign",
            "Input files on `align` command.",
            "inputFilesAlign"
        ) { reports ->
            reports.extract<AlignerReport, _> { it.inputFiles }.flatMap { it.toList() }.joinToString(",")
        }
        this += Field(
            100_100,
            "-totalReads",
            "Count of reads in original data.",
            "totalReads"
        ) { reports ->
            reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum().toString()
        }
        this += FieldsCollection(
            100_200,
            "-patternMatchedReads",
            "Percentage of reads that match pattern."
        ) {
            columnsForPercentage(
                "patternMatchedReads",
                { reports -> reports.extract<AlignerReport, _> { it.tagParsingReport?.matchedReads ?: 0L }.sum() },
                { reports -> reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
//        this += Field(
//            100_300,
//            "-totalUMIs",
//            "Count of found UMIs.",
//            "totalUMIs"
//        ) { reports ->
//        }
//        this += Field(
//            100_400,
//            "-readsPerUMIThreshold",
//            "Thresholds for filter UMIs.",
//            "readsPerUMIThreshold"
//        ) { reports ->
//        }
//        this += Field(
//            100_500,
//            "-totalUMIsAfterCorrectionAndFiltering",
        //may be it must be a result of last command with .vdjca output
//            "Count of UMIs after all filtering and corrections.",
//            "totalUMIsAfterCorrectionAndFiltering"
//        ) { reports ->
//        }
        this += FieldsCollection(
            100_600,
            "-overlapped",
            "Percentage of overlapped reads."
        ) {
            columnsForPercentage(
                "overlapped",
                { reports -> reports.extract<AlignerReport, _> { it.overlapped }.sum() },
                { reports -> reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
        this += FieldsCollection(
            100_700,
            "-overlappedAndAligned",
            "Percentage of overlapped and aligned reads."
        ) {
            columnsForPercentage(
                "overlappedAndAligned",
                { reports -> reports.extract<AlignerReport, _> { it.overlappedAligned }.sum() },
                { reports -> reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
        this += FieldsCollection(
            100_800,
            "-alignmentsFailed",
            "Percentage of reads that not aligned because of different reasons (columns for each reason)."
        ) {
            VDJCAlignmentFailCause.values()
                .flatMap { reason ->
                    columnsForPercentage(
                        "alignmentFailed$reason",
                        { reports -> reports.extract<AlignerReport, _> { it.notAlignedReasons[reason] ?: 0L }.sum() },
                        { reports -> reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum() }
                    )
                }
        }
        this += FieldsCollection(
            100_900,
            "-successAligned",
            "Percentage of aligned reads."
        ) {
            columnsForPercentage(
                "successAligned",
                { reports -> reports.extract<AlignerReport, _> { it.aligned }.sum() },
                { reports -> reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
        this += FieldsCollection(
            101_000,
            "-readsWithChain",
            "Percentage of reads aligned on specific chain. Will be exported all found chains."
        ) {
            allReports.collection.allReports()
                .extract<AlignerReport, _> { it.chainUsage.chains.keys }
                .flatten()
                .distinct()
                .sorted()
                .flatMap { chains ->
                    columnsForPercentage(
                        "readsWithChain$chains",
                        { reports ->
                            reports.extract<AlignerReport, _> { it.chainUsage.chains[chains]?.total ?: 0L }.sum()
                        },
                        { reports -> reports.extract<AlignerReport, _> { it.aligned }.sum() }
                    )
                }
        }
        this += Field(
            200_000,
            "-inputFileAssemble",
            "Input files on `assemble` command.",
            "inputFileAssemble"
        ) { reports ->
            reports.extract<CloneAssemblerReport, _> { it.inputFiles }.flatMap { it.toList() }.joinToString(",")
        }
        this += FieldsCollection(
            200_100,
            "-readsClusteredInCorrection",
            "Reads pre-clustered due to the similar VJC-lists, percent of used."
        ) {
            columnsForPercentage(
                "readsClusteredInCorrection",
                { reports -> reports.extract<CloneAssemblerReport, _> { it.readsPreClustered }.sum() },
                { reports -> reports.extract<CloneAssemblerReport, _> { it.readsInClones }.sum() },
            )
        }
        this += FieldsCollection(
            200_200,
            "-droppedNoClonalSeq",
            "Reads dropped due to the lack of a clone sequence, percent of total."
        ) {
            columnsForPercentage(
                "droppedNoClonalSeq",
                { reports -> reports.extract<CloneAssemblerReport, _> { it.readsDroppedNoTargetSequence }.sum() },
                { reports -> reports.extract<CloneAssemblerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
        this += FieldsCollection(
            200_300,
            "-droppedShortSeq",
            "Reads dropped due to a too short clonal sequence, percent of total."
        ) {
            columnsForPercentage(
                "droppedShortSeq",
                { reports -> reports.extract<CloneAssemblerReport, _> { it.readsDroppedTooShortClonalSequence }.sum() },
                { reports -> reports.extract<CloneAssemblerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
        this += FieldsCollection(
            200_400,
            "-droppedLowQual",
            "Reads dropped due to low quality, percent of total."
        ) {
            columnsForPercentage(
                "droppedLowQual",
                { reports -> reports.extract<CloneAssemblerReport, _> { it.clonesDroppedAsLowQuality }.sum().toLong() },
                { reports -> reports.extract<CloneAssemblerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
        this += FieldsCollection(
            200_500,
            "-droppedFailedMapping",
            "Reads dropped due to failed mapping, percent of total"
        ) {
            columnsForPercentage(
                "droppedFailedMapping",
                { reports -> reports.extract<CloneAssemblerReport, _> { it.readsDroppedFailedMapping }.sum() },
                { reports -> reports.extract<CloneAssemblerReport, _> { it.totalReadsProcessed }.sum() }
            )
        }
        this += Field(
            300_000,
            "-inputFileAssembleContigs",
            "Input files on `assembleContigs` command.",
            "inputFileAssembleContigs"
        ) { reports ->
            reports.extract<FullSeqAssemblerReport, _> { it.inputFiles }.flatMap { it.toList() }.joinToString(",")
        }
        this += Field(
            400_000,
            "-totalClonotypes",
            "Total clonotypes after `assembleContigs` command if it was run, `assemble` otherwise.",
            "totalClonotypes"
        ) { reports ->
            val assembleContigsClones = reports.extract<FullSeqAssemblerReport, _> { it.finalCloneCount }
                .takeIf { it.isNotEmpty() }?.sum()
            val assembleClones = reports.extract<CloneAssemblerReport, _> { it.clones }
                .takeIf { it.isNotEmpty() }?.sum()
            (assembleContigsClones ?: assembleClones)?.toString() ?: ""
        }
        this += Field(
            400_100,
            "-readsUsedInClonotypes",
            "Reads used in clonotypes after `assembleContigs` command if it was run, `assemble` otherwise.",
            "readsUsedInClonotypes"
        ) { reports ->
            val assembleContigsClones = reports.extract<FullSeqAssemblerReport, _> { it.readsClustered }
                .takeIf { it.isNotEmpty() }?.sum()
            val assembleClones = reports.extract<CloneAssemblerReport, _> { it.readsInClones }
                .takeIf { it.isNotEmpty() }?.sum()
            (assembleContigsClones ?: assembleClones)?.toString() ?: ""
        }
//        this += Field(
//            400_200,
//            "-UMIsUsedInClonotypes",
//            "UMIs used in clonotypes after `assembleContigs` command if it was run, `assemble` otherwise.",
//            "UMIsUsedInClonotypes"
//        ) { reports ->
//        }
        this += FieldsCollection(
            400_300,
            "-clonesWithChain",
            "Percentage of clones aligned on specific chain (`assemble` command). Will be exported all found chains.",
        ) {
            allReports.collection.allReports()
                .extract<CloneAssemblerReport, _> { it.clonalChainUsage.chains.keys }
                .flatten()
                .distinct()
                .sorted()
                .flatMap { chains ->
                    //TODO write the same info into FullSeqAssemblerReport
                    columnsForPercentage(
                        "clonesWithChain$chains",
                        { reports ->
                            reports
                                .extract<CloneAssemblerReport, _> { it.clonalChainUsage.chains[chains]?.total ?: 0L }
                                .sum()
                        },
                        { reports -> reports.extract<CloneAssemblerReport, _> { it.clones }.sum().toLong() }
                    )
                }
        }
//        this += Field(
//            700,
//            "-readsPerUmi",
//            "Average count of reads per UMI.",
//            "readsPerUmi"
//        ) { reports ->
//            reports.extract<RefineTagsAndSortReport, _> { it.correctionReport.steps }
//            asPercentage(
//                reports.extract<AlignerReport, _> { it.aligned }.sum(),
//                reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum()
//            )
//        }
        this += Field(
            1_000_000,
            "-foundAllelesCount",
            "Count of found alleles.",
            "foundAllelesCount"
        ) { reports ->
            reports.extract<FindAllelesReport, _> { it.foundAlleles }
                .distinct()
                .sorted()
                .joinToString(",") { it.toString() }
        }
        this += Field(
            1_100_000,
            "-foundTreesCount",
            "Count of found trees.",
            "foundTreesCount"
        ) { reports ->
            reports.extract<BuildSHMTreeReport, _> { it.totalTreesCount() }.sum().toString()
        }
        this += Field(
            1_100_100,
            "-cloneInTreesCount",
            "Count of uniq clones that was included in trees.",
            "cloneInTreesCount"
        ) { reports ->
            reports.extract<BuildSHMTreeReport, _> { it.totalClonesCountInTrees() }.sum().toString()
        }
    }

    private fun <T : Any> columnsForPercentage(
        header: String,
        value: RowMetaForExport.(T) -> Long,
        base: RowMetaForExport.(T) -> Long
    ): List<FieldExtractor<T>> =
        listOf(
            FieldExtractor(header) { reports ->
                value(reports).toString()
            },
            FieldExtractor("${header}Percents") { reports ->
                asPercentage(
                    value(reports),
                    base(reports)
                )
            }
        )

    class ReportsWithSource(
        val source: String,
        val reports: List<MiXCRCommandReport>
    ) {
        inline fun <reified T : MiXCRCommandReport, R> extract(function: (T) -> R): List<R> =
            reports
                .filterIsInstance<T>()
                .map(function)
    }

    private inline fun <reified T : MiXCRCommandReport, R> List<MiXCRCommandReport>.extract(function: (T) -> R): List<R> =
        filterIsInstance<T>()
            .map(function)
}
