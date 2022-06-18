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

import cc.redberry.pipe.OutputPortCloseable
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.VDJCFileHeaderData
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.blocks.PrimitivIHybrid
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readMap
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.dto.VDJCLibraryData
import java.nio.file.Paths
import java.util.*

class SHMTreesReader(
    private val input: PrimitivIHybrid,
    libraryRegistry: VDJCLibraryRegistry
) : AutoCloseable by input, VDJCFileHeaderData {
    val assemblerParameters: CloneAssemblerParameters
    val alignerParameters: VDJCAlignerParameters
    private val tagsInfo: TagsInfo
    val fileNames: List<String>
    private val versionInfo: String
    private val treesPosition: Long
    override fun getTagsInfo(): TagsInfo = tagsInfo

    constructor(input: String, libraryRegistry: VDJCLibraryRegistry) : this(
        PrimitivIHybrid(Paths.get(input), 3),
        libraryRegistry
    )

    init {
        input.beginPrimitivI(true).use { i ->
            val magicBytes = ByteArray(SHMTreesWriter.MAGIC_LENGTH)
            i.readFully(magicBytes)
            when (val magicString = String(magicBytes)) {
                SHMTreesWriter.MAGIC -> {}
                else -> throw RuntimeException(
                    "Unsupported file format; .clns file of version " + magicString +
                            " while you are running MiXCR " + SHMTreesWriter.MAGIC
                )
            }
        }

        input.beginRandomAccessPrimitivI(-IOUtil.END_MAGIC_LENGTH.toLong()).use { pi ->
            // Checking file consistency
            val endMagic = ByteArray(IOUtil.END_MAGIC_LENGTH)
            pi.readFully(endMagic)
            if (!Arrays.equals(IOUtil.getEndMagicBytes(), endMagic)) throw RuntimeException("Corrupted file.")
        }

        input.beginPrimitivI(true).use { i ->
            versionInfo = i.readUTF()
            assemblerParameters = i.readObjectRequired()
            alignerParameters = i.readObjectRequired()
            tagsInfo = i.readObjectRequired()
            val liberalise = i.readMap<String, VDJCLibraryData>()
            liberalise.forEach { (name: String, libraryData: VDJCLibraryData) ->
                libraryRegistry.registerLibrary(null, name, libraryData)
            }
            fileNames = i.readList()
            IOUtil.stdVDJCPrimitivIStateInit(i, alignerParameters, libraryRegistry)
        }

        treesPosition = input.position
    }

    fun readTrees(): OutputPortCloseable<SHMTreeResult> =
        input.beginRandomAccessPrimitivIBlocks(SHMTreeResult::class.java, treesPosition)

}
