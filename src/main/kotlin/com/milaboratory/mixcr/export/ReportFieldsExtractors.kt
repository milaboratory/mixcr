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
import com.milaboratory.mixcr.cli.AlignerReport
import com.milaboratory.mixcr.cli.CloneAssemblerReport
import com.milaboratory.mixcr.cli.CommandExportReportsAsTable
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.export.ParametersFactory.chainsParam
import com.milaboratory.mixcr.export.ReportFieldsExtractors.ReportsWithSource
import com.milaboratory.mixcr.trees.BuildSHMTreeReport
import com.milaboratory.util.ReportHelper
import io.repseq.core.Chains

private fun Double.formatPercentage(): String = ReportHelper.PERCENT_FORMAT.format(this * 100)

private fun asPercentage(value: Long, base: Long) = (value / base.toDouble()).formatPercentage()

object ReportFieldsExtractors : FieldExtractorsFactoryWithPresets<ReportsWithSource>() {
    override val presets: Map<String, List<ExportFieldDescription>> = buildMap {
        this["min"] = listOf(
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-MiXCRVersion"),
            ExportFieldDescription("-sourceReadsTotal"),
            ExportFieldDescription("-alignedReads"),
        )
        this["full"] = listOf(
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-MiXCRVersion"),
            ExportFieldDescription("-sourceReadsTotal"),
            ExportFieldDescription("-alignedReads"),
            ExportFieldDescription("-readsWithChain"),
            ExportFieldDescription("-clonesWithChain"),
            ExportFieldDescription("-foundAllelesCount"),
            ExportFieldDescription("-foundTreesCount"),
            ExportFieldDescription("-cloneInTreesCount"),
        )
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
            300,
            "-sourceReadsTotal",
            "Count of reads in original data.",
            "sourceReadsTotal"
        ) { reports ->
            reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum().toString()
        }
        this += Field(
            400,
            "-alignedReads",
            "Percentage of aligned reads.",
            "alignedReads"
        ) { reports ->
            asPercentage(
                reports.extract<AlignerReport, _> { it.aligned }.sum(),
                reports.extract<AlignerReport, _> { it.totalReadsProcessed }.sum()
            )
        }
        this += FieldsCollection(
            500,
            "-readsWithChain",
            "Percentage of reads aligned on specific chain. Will be exported all found chains.",
            Field(
                //delegate can't be used separately
                -1, "-", "none", chainsParam("readsWithChain")
            ) { reports: ReportsWithSource, chains: Chains ->
                asPercentage(
                    reports.extract<AlignerReport, _> { it.chainUsage.chains[chains]!!.total }.sum(),
                    reports.extract<AlignerReport, _> { it.aligned }.sum()
                )
            }
        ) {
            allReports.collection.allReports()
                .extract<AlignerReport, _> { it.chainUsage.chains.keys }
                .flatten()
                .distinct()
                .sorted()
                .map { arrayOf(it.toString()) }
        }
        this += FieldsCollection(
            600,
            "-clonesWithChain",
            "Percentage of clones aligned on specific chain. Will be exported all found chains.",
            Field(
                //delegate can't be used separately
                -1, "-", "none", chainsParam("clonesWithChain")
            ) { reports: ReportsWithSource, chains: Chains ->
                asPercentage(
                    reports.extract<CloneAssemblerReport, _> { it.clonalChainUsage.chains[chains]!!.total }.sum(),
                    reports.extract<CloneAssemblerReport, _> { it.clones }.sum().toLong()
                )
            }
        ) {
            allReports.collection.allReports()
                .extract<CloneAssemblerReport, _> { it.clonalChainUsage.chains.keys }
                .flatten()
                .distinct()
                .sorted()
                .map { arrayOf(it.toString()) }
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
            400_000,
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
            500_000,
            "-foundTreesCount",
            "Count of found trees.",
            "foundTreesCount"
        ) { reports ->
            reports.extract<BuildSHMTreeReport, _> { it.totalTreesCount() }.sum().toString()
        }
        this += Field(
            500_100,
            "-cloneInTreesCount",
            "Count of uniq clones that was included in trees.",
            "cloneInTreesCount"
        ) { reports ->
            reports.extract<BuildSHMTreeReport, _> { it.totalClonesCountInTrees() }.sum().toString()
        }
    }

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
