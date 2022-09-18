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
import picocli.CommandLine

/**
 *
 */
@CommandLine.Command(
    name = CommandExportAlignmentsForClones.COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Export alignments for particular clones from \"clones & alignments\" (*.clna) file."]
)
class CommandExportAlignmentsForClones : AbstractMiXCRCommand() {
    @CommandLine.Parameters(index = "0", description = ["clones.clna"])
    lateinit var `in`: String

    @CommandLine.Parameters(index = "1", description = ["alignments.vdjca"])
    lateinit var out: String

    @CommandLine.Option(names = ["--id"], description = ["[cloneId1 [cloneId2 [cloneId3]]]"], arity = "0..*")
    var ids: List<Int> = mutableListOf()
    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    private val cloneIds: IntArray
        get() = ids.sorted().toIntArray()

    override fun run0() {
        ClnAReader(`in`, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { clna ->
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

    companion object {
        const val COMMAND_NAME = "exportAlignmentsForClones"
    }
}
