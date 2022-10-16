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
import com.milaboratory.mitool.pattern.search.readObject
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRFileInfo
import com.milaboratory.mixcr.basictypes.MiXCRFooter
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.VirtualCloneSet
import com.milaboratory.mixcr.cli.ApplicationException
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.blocks.PrimitivIHybrid
import com.milaboratory.primitivio.onEach
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibraryId
import io.repseq.core.VDJCLibraryRegistry
import java.nio.file.Path
import java.util.*

class SHMTreesReader(
    private val input: PrimitivIHybrid,
    val libraryRegistry: VDJCLibraryRegistry
) : AutoCloseable by input, MiXCRFileInfo {
    val fileNames: List<String>
    val cloneSetInfos: List<VirtualCloneSet>
    override val header: MiXCRHeader
    val userGenes: List<VDJCGene>
    override val footer: MiXCRFooter
    val versionInfo: String
    private val treesPosition: Long

    constructor(input: Path, libraryRegistry: VDJCLibraryRegistry) : this(
        PrimitivIHybrid(input, 3),
        libraryRegistry
    )

    init {
        input.beginPrimitivI(true).use { i ->
            val magicBytes = ByteArray(SHMTreesWriter.MAGIC_LENGTH)
            i.readFully(magicBytes)
            when (val magicString = String(magicBytes)) {
                SHMTreesWriter.MAGIC -> {}
                else -> throw ApplicationException(
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
            header = i.readObjectRequired()
            fileNames = i.readList(PrimitivI::readObjectRequired)
            cloneSetInfos = i.readList(PrimitivI::readObjectRequired)

            val libraries = cloneSetInfos.mapNotNull { it.header.foundAlleles }
            libraries.forEach { (name, libraryData) ->
                val alreadyRegistered = libraryRegistry.loadedLibraries.stream()
                    .anyMatch {
                        it.libraryId.withoutChecksum() == VDJCLibraryId(name, libraryData.taxonId)
                    }
                if (!alreadyRegistered)
                    libraryRegistry.registerLibrary(null, name, libraryData)
            }

            userGenes = IOUtil.stdVDJCPrimitivIStateInit(i, header.alignerParameters, libraryRegistry)
        }

        input.beginRandomAccessPrimitivI(reportsStartPosition).use { pi ->
            footer = pi.readObject<MiXCRFooter>()
        }

        treesPosition = input.position
    }

    val alignerParameters get() = header.alignerParameters

    fun readTrees(): OutputPortCloseable<SHMTreeResult> =
        input.beginRandomAccessPrimitivIBlocks(SHMTreeResult::class.java, treesPosition)
            .onEach { tree ->
                tree.tree.allNodes().forEach { (_, node) ->
                    node.content.clones.forEach { (clone, datasetId) ->
                        clone.parentCloneSet = cloneSetInfos[datasetId]
                    }
                }
            }
}
