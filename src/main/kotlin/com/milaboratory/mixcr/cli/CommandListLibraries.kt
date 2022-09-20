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

import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine

@CommandLine.Command(
    name = "listLibraries",
    sortOptions = true,
    hidden = true,
    separator = " ",
    description = ["List all available library by scanning all library search paths."]
)
class CommandListLibraries : AbstractMiXCRCommand() {
    override fun getInputFiles(): List<String> = emptyList()

    override fun getOutputFiles(): List<String> = emptyList()

    override fun run0() {
        VDJCLibraryRegistry.getDefault().loadAllLibraries()
        println("Available libraries:")
        val loadedLibraries = VDJCLibraryRegistry.getDefault().loadedLibraries
        for (library in loadedLibraries.sorted()) {
            println(library.libraryId)
            println(VDJCLibraryRegistry.getDefault().getSpeciesNames(library.taxonId))
        }
    }
}
