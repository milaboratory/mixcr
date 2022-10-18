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
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.primitivio.forEach
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

/**
 *
 */
@Command(
    description = [
        "Export alignments for particular clones from \"clones & alignments\" (*.clna) file.",
        "Note that such export is possible only from `.clna` files, produced by MiXCR `assemble` command with option `--write-alignments`."
    ]
)
class CommandExportAlignmentsForClones : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Path to input clna file"],
        index = "0",
        paramLabel = "clones.clna"
    )
    lateinit var input: Path

    @Parameters(
        description = ["Path where to write alignments from input file"],
        index = "1",
        paramLabel = "alignments.vdjca"
    )
    lateinit var out: Path

    @Option(
        description = [
            "Clone ids to export.",
            "If no clone ids are specified all alignments will be exported."
        ],
        names = ["--id"],
        paramLabel = "<id>",
        arity = "0..*"
    )
    var ids: List<Int> = mutableListOf()

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOf(out)

    private val cloneIds: IntArray
        get() = ids.sorted().toIntArray()

    override fun run0() {
        ClnAReader(input, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { clna ->
            VDJCAlignmentsWriter(out).use { writer ->
                writer.writeHeader(clna.header, clna.usedGenes)
                var count: Long = 0
                if (cloneIds.isEmpty()) {
                    clna.readAllAlignments().forEach { al ->
                        if (al.cloneIndex == -1L) return@forEach
                        writer.write(al)
                        ++count
                    }
                } else {
                    for (id in cloneIds) {
                        val reader = clna.readAlignmentsOfClone(id)
                        reader.forEach { al ->
                            writer.write(al)
                            ++count
                        }
                    }
                }
                writer.setNumberOfProcessedReads(count)
                writer.setFooter(clna.footer)
            }
        }
    }

}
