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
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRMetaInfo
import com.milaboratory.mixcr.basictypes.ReportsFooterData
import com.milaboratory.mixcr.basictypes.VDJCFileHeaderData
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.blocks.PrimitivIHybrid
import com.milaboratory.primitivio.readList
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibraryId
import io.repseq.core.VDJCLibraryRegistry
import java.nio.file.Path
import java.util.*

class SHMTreesReader(
    private val input: PrimitivIHybrid,
    val libraryRegistry: VDJCLibraryRegistry
) : AutoCloseable by input, VDJCFileHeaderData, ReportsFooterData {
    val fileNames: List<String>
    val alignerParameters: VDJCAlignerParameters
    val metaInfo: List<MiXCRMetaInfo>
    val userGenes: List<VDJCGene>
    private val reports: List<MiXCRCommandReport>
    override val tagsInfo: TagsInfo
    val versionInfo: String
    private val treesPosition: Long

    override fun reports(): List<MiXCRCommandReport> = reports

    constructor(input: Path, libraryRegistry: VDJCLibraryRegistry) : this(
        PrimitivIHybrid(input, 3),
        libraryRegistry
    )

    init {
        input.beginPrimitivI(true).use { i ->
            val magicBytes = ByteArray(SHMTreesWriter.MAGIC_LENGTH)
            i.readFully(magicBytes)
            when (val magicString = String(magicBytes)) {
                SHMTreesWriter.OLD_MAGIC_V1 -> {}
                SHMTreesWriter.MAGIC -> {}
                else -> throw RuntimeException(
                    "Unsupported file format; .shmt file of version " + magicString +
                            " while you are running MiXCR " + SHMTreesWriter.MAGIC
                )
            }.exhaustive
        }

        val reportsStartPosition: Long
        input.beginRandomAccessPrimitivI(-SHMTreesWriter.FOOTER_LENGTH.toLong()).use { pi ->
            reportsStartPosition = pi.readLong()
            // Checking file consistency
            val endMagic = ByteArray(IOUtil.END_MAGIC_LENGTH)
            pi.readFully(endMagic)
            if (!Arrays.equals(IOUtil.getEndMagicBytes(), endMagic)) throw RuntimeException("Corrupted file.")
        }


        input.beginPrimitivI(true).use { i ->
            versionInfo = i.readUTF()
            metaInfo = i.readList()
            fileNames = i.readList()

            // TODO resolve different tags info for different source files
            check(metaInfo.map { it.tagsInfo }.distinct().size == 1)
            tagsInfo = metaInfo.first().tagsInfo

            check(metaInfo.map { it.alignerParameters }.distinct().size == 1)
            alignerParameters = metaInfo.first().alignerParameters

            val libraries = metaInfo.mapNotNull { it.foundAlleles }
            libraries.forEach { (name, libraryData) ->
                val alreadyRegistered = libraryRegistry.loadedLibraries.stream()
                    .anyMatch {
                        it.libraryId.withoutChecksum() == VDJCLibraryId(name, libraryData.taxonId)
                    }
                if (!alreadyRegistered)
                    libraryRegistry.registerLibrary(null, name, libraryData)
            }
            userGenes = IOUtil.stdVDJCPrimitivIStateInit(i, alignerParameters, libraryRegistry)
        }

        input.beginRandomAccessPrimitivI(reportsStartPosition).use { pi ->
            reports = pi.readList()
        }

        treesPosition = input.position
    }

    fun readTrees(): OutputPortCloseable<SHMTreeResult> =
        input.beginRandomAccessPrimitivIBlocks(SHMTreeResult::class.java, treesPosition)

}
