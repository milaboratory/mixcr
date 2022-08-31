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
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.nio.file.Paths

@CommandLine.Command(
    name = "versionInfo",
    separator = " ",
    description = ["Output information about MiXCR version which generated the file."]
)
class CommandVersionInfo : MiXCRCommand() {
    @CommandLine.Parameters(description = ["input_file"])
    lateinit var inputFile: String

    override fun getInputFiles(): List<String> = listOf(inputFile)

    override fun getOutputFiles(): List<String> = emptyList()

    override fun run0() {
        when (IOUtil.extractFileType(Paths.get(inputFile))) {
            VDJCA -> VDJCAlignmentsReader(inputFile).use { reader ->
                reader.ensureInitialized()
                println("MagicBytes = " + reader.magic)
                println(reader.versionInfo)
            }
            CLNS -> {
                val cs = CloneSetIO.read(inputFile)
                println(cs.versionInfo)
            }
            CLNA -> ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), 1)
                .use { reader -> println(reader.versionInfo) }
            else -> throwValidationExceptionKotlin("Wrong file type.")
        }
    }
}
