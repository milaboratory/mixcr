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
package com.milaboratory.mixcr.cli.postanalysis

import com.milaboratory.mitool.helpers.drainToAndClose
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.cli.AbstractMiXCRCommand
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.cli.ValidationException
import com.milaboratory.mixcr.postanalysis.SetPreprocessor
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary
import com.milaboratory.mixcr.postanalysis.ui.ClonotypeDataset
import com.milaboratory.mixcr.postanalysis.ui.DownsamplingParameters
import com.milaboratory.primitivio.port
import com.milaboratory.primitivio.toList
import io.repseq.core.Chains
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@CommandLine.Command(name = "downsample", separator = " ", description = ["Downsample clonesets."])
class CommandDownsample : AbstractMiXCRCommand() {
    @CommandLine.Parameters(description = ["cloneset.{clns|clna}..."], arity = "1..*")
    lateinit var `in`: List<String>

    @CommandLine.Option(description = ["Specify chains"], names = ["-c", "--chains"], required = true)
    var chains: String? = null

    @CommandLine.Option(description = [CommonDescriptions.ONLY_PRODUCTIVE], names = ["--only-productive"])
    var onlyProductive = false

    @CommandLine.Option(description = [CommonDescriptions.DOWNSAMPLING], names = ["--downsampling"], required = true)
    lateinit var downsampling: String

    @CommandLine.Option(
        description = ["Write downsampling summary tsv/csv table."],
        names = ["--summary"],
        required = false
    )
    var summary: String? = null

    @CommandLine.Option(description = ["Suffix to add to output clns file."], names = ["--suffix"])
    var suffix = "downsampled"

    @CommandLine.Option(description = ["Output path prefix."], names = ["--out"])
    var out: String? = null

    private val outPath: Path?
        get() = out?.let { Paths.get(it) }

    override val inputFiles: List<String>
        get() = `in`

    override val outputFiles: List<String>
        get() = inputFiles.map { output(it).toString() }

    override fun validate() {
        super.validate()
        summary?.let { summary ->
            if (!summary.endsWith(".tsv") && !summary.endsWith(".csv"))
                throw ValidationException("summary table should ends with .csv/.tsv")
        }
    }

    private fun output(input: String): Path {
        val fileNameWithoutExtension = Paths.get(input).fileName.toString()
            .replace(".clna", "")
            .replace(".clns", "")
        val outName = "$fileNameWithoutExtension.$chains.$suffix.clns"
        return (outPath?.resolve(outName) ?: Paths.get(outName)).toAbsolutePath()
    }

    private fun ensureOutputPathExists() {
        if (outPath != null) {
            Files.createDirectories(outPath!!.toAbsolutePath())
        }
        if (summary != null) {
            Files.createDirectories(Paths.get(summary!!).toAbsolutePath().parent)
        }
    }

    override fun run0() {
        val datasets = `in`.map { file -> ClonotypeDataset(file, file, VDJCLibraryRegistry.getDefault()) }
        val preprocessor = DownsamplingParameters
            .parse(downsampling, CommandPa.extractTagsInfo(inputFiles), false, onlyProductive)
            .getPreprocessor(Chains.parse(chains))
            .newInstance()
        val results = SetPreprocessor.processDatasets(preprocessor, datasets)
        ensureOutputPathExists()
        for (i in results.indices) {
            ClnsWriter(output(`in`[i]).toFile()).use { clnsWriter ->
                val downsampled = results[i].mkElementsPort().toList()
                clnsWriter.writeHeader(
                    datasets[i].header,
                    datasets[i].ordering(),
                    datasets[i].usedGenes,
                    downsampled.size
                )
                downsampled.port.drainToAndClose(clnsWriter.cloneWriter())
                clnsWriter.setFooter(datasets[i].footer)
            }
        }
        val summaryStat = preprocessor.stat
        for (i in results.indices) {
            val stat = SetPreprocessorStat.cumulative(summaryStat[i])
            println(
                `in`[i] + ":" +
                        " isDropped=" + stat.dropped +
                        " nClonesBefore=" + stat.nElementsBefore +
                        " nClonesAfter=" + stat.nElementsAfter +
                        " sumWeightBefore=" + stat.sumWeightBefore +
                        " sumWeightAfter=" + stat.sumWeightAfter
            )
        }
        if (summary != null) {
            val summaryTable = `in`.withIndex().associate { (i, file) -> file to summaryStat[i] }
            SetPreprocessorSummary.toCSV(
                Paths.get(summary!!).toAbsolutePath(),
                SetPreprocessorSummary(summaryTable),
                if (summary!!.endsWith("csv")) "," else "\t"
            )
        }
    }
}
