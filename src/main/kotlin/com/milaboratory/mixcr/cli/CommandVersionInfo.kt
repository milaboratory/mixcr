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

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.util.exhaustive
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    name = "versionInfo",
    description = ["Output information about MiXCR version which generated the file."]
)
class CommandVersionInfo : MiXCRCommand() {
    @Parameters(paramLabel = "input_file.(vdjca|clns|clna|shmt)")
    lateinit var inputFile: Path

    override fun validate() {
        ValidationException.requireFileType(inputFile, InputFileType.VDJCA, InputFileType.CLNX, InputFileType.SHMT)
    }

    override fun run0() {
        when (IOUtil.extractFileType(inputFile)) {
            VDJCA -> VDJCAlignmentsReader(inputFile).use { reader ->
                reader.ensureInitialized()
                println("MagicBytes = " + reader.magic)
                println(reader.versionInfo)
            }
            CLNS -> ClnsReader(inputFile, VDJCLibraryRegistry.getDefault())
                .use { reader -> println(reader.versionInfo) }
            CLNA -> ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), 1)
                .use { reader -> println(reader.versionInfo) }
            SHMT -> SHMTreesReader(inputFile, VDJCLibraryRegistry.getDefault())
                .use { reader -> println(reader.versionInfo) }
        }.exhaustive
    }
}
