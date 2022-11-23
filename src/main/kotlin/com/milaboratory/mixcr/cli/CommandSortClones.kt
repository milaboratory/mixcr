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

import com.milaboratory.mitool.exhaustive
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
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
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    description = ["Sort clones by sequence. Clones in the output file will be sorted by clonal sequence, which allows to build overlaps between clonesets."]
)
class CommandSortClones : MiXCRCommandWithOutputs() {
    @Parameters(paramLabel = "clones.(clns|clna)", index = "0")
    lateinit var input: Path

    @Parameters(paramLabel = "clones.sorted.(clns|clna)", index = "1")
    lateinit var out: Path

    @Mixin
    lateinit var useLocalTemp: UseLocalTempOption

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOf(out)

    override fun validate() {
        ValidationException.requireTheSameFileType(input, out, InputFileType.CLNS, InputFileType.CLNA)
    }


    override fun run0() {
        when (IOUtil.extractFileType(input)) {
            CLNS -> ClnsReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
                ClnsWriter(out).use { writer ->
                    val assemblingFeatures =
                        sortGeneFeaturesContainingCDR3First(reader.assemblerParameters.assemblingFeatures)
                    val ordering = VDJCSProperties.cloneOrderingByNucleotide(
                        assemblingFeatures,
                        Variable, Joining
                    )
                    writer.writeCloneSet(CloneSet.reorder(reader.readCloneSet(), ordering))
                    writer.setFooter(reader.footer)
                }
            }
            CLNA -> ClnAReader(
                input,
                VDJCLibraryRegistry.getDefault(),
                Runtime.getRuntime().availableProcessors()
            ).use { reader ->
                ClnAWriter(out, TempFileManager.smartTempDestination(out, "", !useLocalTemp.value)).use { writer ->
                    SmartProgressReporter.startProgressReport(writer)
                    val assemblingFeatures =
                        sortGeneFeaturesContainingCDR3First(reader.assemblerParameters.assemblingFeatures)
                    val ordering = VDJCSProperties.cloneOrderingByNucleotide(
                        assemblingFeatures,
                        Variable, Joining
                    )
                    writer.writeClones(CloneSet.reorder(reader.readCloneSet(), ordering))
                    writer.collateAlignments(reader.readAllAlignments(), reader.numberOfAlignments())
                    writer.setFooter(reader.footer)
                    writer.writeAlignmentsAndIndex()
                }
            }
            VDJCA -> throw ValidationException("File type is not supported by this command")
            SHMT -> throw ValidationException("File type is not supported by this command")
        }.exhaustive
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
