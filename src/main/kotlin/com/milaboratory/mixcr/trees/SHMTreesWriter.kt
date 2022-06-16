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
package com.milaboratory.mixcr.trees

import cc.redberry.pipe.InputPort
import com.milaboratory.cli.AppVersionInfo
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.Util
import com.milaboratory.primitivio.blocks.PrimitivOHybrid
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibrary
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class SHMTreesWriter(
    private val output: PrimitivOHybrid
) : AutoCloseable {
    constructor(fileName: String) : this(PrimitivOHybrid(Paths.get(fileName)))

    //TODO write less
    fun writeHeader(
        assemblerParameters: CloneAssemblerParameters,
        alignerParameters: VDJCAlignerParameters,
        fileNames: List<String>,
        genes: List<VDJCGene>,
        tagInfo: TagsInfo,
        libraries: List<VDJCLibrary>
    ) {
        output.beginPrimitivO(true).use { o ->
            // Writing magic bytes
            o.write(MAGIC_BYTES)

            // Writing version information
            o.writeUTF(MiXCRVersionInfo.get().getVersionString(AppVersionInfo.OutputType.ToFile))

            // Writing analysis meta-information
            o.writeObject(assemblerParameters)
            o.writeObject(alignerParameters)
            o.writeObject(tagInfo)
            Util.writeMap(
                libraries.associateBy({ obj -> obj.name }, { obj -> obj.data }),
                o
            )
            Util.writeList(fileNames, o)
            IOUtil.stdVDJCPrimitivOStateInit(o, genes, alignerParameters)
        }
    }

    /**
     * Must be closed by putting null
     */
    fun treesWriter(): InputPort<SHMTree> = output.beginPrimitivOBlocks(3, 512)

    override fun close() {
        output.beginPrimitivO().use { o ->
            // Writing end-magic as a file integrity sign
            o.write(IOUtil.getEndMagicBytes())
        }
        output.close()
    }

    companion object {
        const val MAGIC_V0 = "MiXCR.TREE.V00"
        const val MAGIC = MAGIC_V0
        const val MAGIC_LENGTH = 14
        val MAGIC_BYTES = MAGIC.toByteArray(StandardCharsets.US_ASCII)
    }
}
