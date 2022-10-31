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
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.cli.InputFileType
import com.milaboratory.mixcr.cli.MiXCRCommandWithOutputs
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
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension

@Command(description = ["Downsample clonesets."])
class CommandDownsample : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Paths to input files."],
        paramLabel = "cloneset.(clns|clna)",
        arity = "1..*"
    )
    override val inputFiles: List<Path> = mutableListOf()

    @Option(
        description = ["Specify chains"],
        names = ["-c", "--chains"],
        required = true
    )
    var chains: Chains? = null

    @Option(
        description = [CommonDescriptions.ONLY_PRODUCTIVE],
        names = ["--only-productive"]
    )
    var onlyProductive = false

    @Option(
        description = ["Choose ${CommonDescriptions.DOWNSAMPLING}"],
        names = ["--downsampling"],
        required = true,
        paramLabel = "<type>"
    )
    lateinit var downsampling: String

    @set:Option(
        description = ["Write downsampling summary tsv/csv table."],
        names = ["--summary"],
        paramLabel = "<path>"
    )
    var summary: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.XSV)
            field = value
        }

    @Option(
        description = ["Suffix to add to output clns file."],
        names = ["--suffix"],
        paramLabel = "<s>",
        showDefaultValue = ALWAYS
    )
    var suffix = "downsampled"

    @Option(
        description = ["Output path prefix."],
        names = ["--out"],
        paramLabel = "<path_prefix>"
    )
    var outPath: Path? = null

    override val outputFiles
        get() = inputFiles.map { output(it) }

    private fun output(input: Path): Path {
        val fileNameWithoutExtension = input.fileName.toString()
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
            Files.createDirectories(summary!!.toAbsolutePath().parent)
        }
    }

    override fun validate() {
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.CLNX)
        }
        ValidationException.requireNoExtension(outPath?.toString())
    }

    override fun run0() {
        val datasets =
            inputFiles.map { file -> ClonotypeDataset(file.toString(), file, VDJCLibraryRegistry.getDefault()) }
        val preprocessor = DownsamplingParameters
            .parse(downsampling, CommandPa.extractTagsInfo(inputFiles), false, onlyProductive)
            .getPreprocessor(chains)
            .newInstance()
        val results = SetPreprocessor.processDatasets(preprocessor, datasets)
        ensureOutputPathExists()
        for (i in results.indices) {
            ClnsWriter(output(inputFiles[i]).toFile()).use { clnsWriter ->
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
                inputFiles[i] + ":" +
                        " isDropped=" + stat.dropped +
                        " nClonesBefore=" + stat.nElementsBefore +
                        " nClonesAfter=" + stat.nElementsAfter +
                        " sumWeightBefore=" + stat.sumWeightBefore +
                        " sumWeightAfter=" + stat.sumWeightAfter
            )
        }
        summary?.let { summary ->
            val summaryTable = inputFiles.withIndex().associate { (i, file) -> file.toString() to summaryStat[i] }
            SetPreprocessorSummary.toCSV(
                summary.toAbsolutePath(),
                SetPreprocessorSummary(summaryTable),
                if (summary.extension == "csv") "," else "\t"
            )
        }
    }
}
