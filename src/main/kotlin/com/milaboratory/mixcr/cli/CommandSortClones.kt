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

import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCSProperties
import com.milaboratory.util.ArraysUtils
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.nio.file.Paths

@CommandLine.Command(
    name = CommandSortClones.SORT_CLONES_COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Sort clones by sequence. Clones in the output file will be sorted by clonal sequence, which allows to build overlaps between clonesets."]
)
class CommandSortClones : MiXCRCommand() {
    @CommandLine.Parameters(description = ["clones.[clns|clna]"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["clones.sorted.clns"], index = "1")
    lateinit var out: String

    @CommandLine.Option(
        description = ["Use system temp folder for temporary files, the output folder will be used if this option is omitted."],
        names = ["--use-system-temp"]
    )
    var useSystemTemp = false

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    override fun run0() {
        when (IOUtil.extractFileType(Paths.get(`in`))!!) {
            CLNS -> ClnsReader(Paths.get(`in`), VDJCLibraryRegistry.getDefault()).use { reader ->
                ClnsWriter(out).use { writer ->
                    val assemblingFeatures =
                        sortGeneFeaturesContainingCDR3First(reader.assemblerParameters.assemblingFeatures)
                    val ordering = VDJCSProperties.cloneOrderingByNucleotide(
                        assemblingFeatures,
                        Variable, Joining
                    )
                    writer.writeCloneSet(CloneSet.reorder(reader.cloneSet, ordering))
                    writer.writeFooter(reader.reports(), null)
                }
            }
            CLNA -> ClnAReader(
                Paths.get(`in`),
                VDJCLibraryRegistry.getDefault(),
                Runtime.getRuntime().availableProcessors()
            ).use { reader ->
                ClnAWriter(out, TempFileManager.smartTempDestination(out, "", useSystemTemp)).use { writer ->
                    SmartProgressReporter.startProgressReport(writer)
                    val assemblingFeatures =
                        sortGeneFeaturesContainingCDR3First(reader.assemblerParameters.assemblingFeatures)
                    val ordering = VDJCSProperties.cloneOrderingByNucleotide(
                        assemblingFeatures,
                        Variable, Joining
                    )
                    writer.writeClones(CloneSet.reorder(reader.readCloneSet(), ordering))
                    writer.collateAlignments(reader.readAllAlignments(), reader.numberOfAlignments())
                    writer.writeFooter(reader.reports(), null)
                    writer.writeAlignmentsAndIndex()
                }
            }
            VDJCA -> throwValidationExceptionKotlin("File type is not supported by this command")
        }
    }

    private fun sortGeneFeaturesContainingCDR3First(geneFeatures: Array<GeneFeature>): Array<GeneFeature> {
        val sorted = geneFeatures.clone()

        // Any CDR3 containing feature will become first
        var i = 0
        while (i < sorted.size) {
            if (sorted[i].contains(CDR3)) {
                if (i != 0) ArraysUtils.swap(sorted, 0, i)
                break
            }
            i++
        }
        return sorted
    }

    companion object {
        const val SORT_CLONES_COMMAND_NAME = "sortClones"
    }
}
