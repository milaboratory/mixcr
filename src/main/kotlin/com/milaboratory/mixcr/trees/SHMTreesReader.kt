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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.onEach
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mixcr.MiXCRStepParams
import com.milaboratory.mixcr.MiXCRStepReports
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRFileInfo
import com.milaboratory.mixcr.basictypes.MiXCRFooter
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.VirtualCloneSet
import com.milaboratory.mixcr.cli.ApplicationException
import com.milaboratory.mixcr.util.BackwardCompatibilityUtils
import com.milaboratory.primitivio.blocks.PrimitivIHybrid
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
        var overrideSourceNames = false
        input.beginPrimitivI(true).use { i ->
            val magicBytes = ByteArray(SHMTreesWriter.MAGIC_LENGTH)
            i.readFully(magicBytes)
            when (val magicString = String(magicBytes)) {
                SHMTreesWriter.MAGIC_V4 -> BackwardCompatibilityUtils.register41_0Serializers(i.serializersManager)
                SHMTreesWriter.MAGIC_V5 -> {
                    BackwardCompatibilityUtils.register41_1Serializers(i.serializersManager)
                    overrideSourceNames = true
                }
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
            val originalHeader = i.readObjectRequired<MiXCRHeader>()
            fileNames = i.readList { readObjectRequired() }
            header = if (overrideSourceNames) {
                originalHeader.copy(
                    stepParams = MiXCRStepParams(
                        originalHeader.stepParams.collection.replaceUpstreamFileNames(fileNames)
                    )
                )
            } else {
                originalHeader
            }
            cloneSetInfos = i.readList { readObjectRequired() }

            val libraries = cloneSetInfos.mapNotNull { it.header.foundAlleles }
            libraries.forEach { (name, libraryData) ->
                val alreadyRegistered = libraryRegistry.loadedLibraries.stream()
                    .anyMatch {
                        it.libraryId.withoutChecksum() == VDJCLibraryId(name, libraryData.taxonId)
                    }
                if (!alreadyRegistered)
                    libraryRegistry.registerLibrary(null, name, libraryData)
            }

            userGenes = IOUtil.stdVDJCPrimitivIStateInit(i, header.featuresToAlign, libraryRegistry)
        }

        input.beginRandomAccessPrimitivI(reportsStartPosition).use { pi ->
            val originalFooter = pi.readObjectRequired<MiXCRFooter>()
            footer = if (overrideSourceNames) {
                originalFooter.copy(
                    reports = MiXCRStepReports(originalFooter.reports.collection.replaceUpstreamFileNames(fileNames))
                )
            } else {
                originalFooter
            }
        }

        treesPosition = input.position
    }

    val alignerParameters: HasFeatureToAlign get() = header.featuresToAlign

    fun readTrees(): OutputPort<SHMTreeResult> =
        input.beginRandomAccessPrimitivIBlocks(SHMTreeResult::class.java, treesPosition)
            .onEach { tree ->
                tree.tree.allNodes().forEach { (_, node) ->
                    node.content.clones.forEach { (clone, datasetId) ->
                        clone.parentCloneSet = cloneSetInfos[datasetId]
                    }
                }
            }
}
