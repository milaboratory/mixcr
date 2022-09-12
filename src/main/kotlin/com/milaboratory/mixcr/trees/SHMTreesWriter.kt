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
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRMetaInfo
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.blocks.PrimitivOHybrid
import com.milaboratory.primitivio.writeCollection
import io.repseq.core.VDJCGene
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class SHMTreesWriter(
    private val output: PrimitivOHybrid
) : AutoCloseable {
    private var footer: List<MiXCRCommandReport>? = null

    constructor(fileName: String) : this(PrimitivOHybrid(Paths.get(fileName)))

    fun writeHeader(
        metaInfo: List<MiXCRMetaInfo>,
        alignerParameters: VDJCAlignerParameters,
        fileNames: List<String>,
        genes: List<VDJCGene>
    ) {
        output.beginPrimitivO(true).use { o ->
            // Writing magic bytes
            o.write(MAGIC_BYTES)

            // Writing version information
            o.writeUTF(MiXCRVersionInfo.get().getVersionString(AppVersionInfo.OutputType.ToFile))

            o.writeCollection(metaInfo)
            o.writeCollection(fileNames)
            IOUtil.stdVDJCPrimitivOStateInit(o, genes, alignerParameters)
        }
    }

    /**
     * Must be closed by putting null
     */
    fun treesWriter(): InputPort<SHMTreeResult> = output.beginPrimitivOBlocks(3, 512)

    /**
     * Write reports chain
     */
    fun writeFooter(sourcesReports: Map<String, List<MiXCRCommandReport>>, report: MiXCRCommandReport?) {
        check(this.footer == null) { "Footer already written" }
        val wrappedReports = sourcesReports
            .flatMap { (fileName, reports) -> reports.map { SHMTreeSourceFileReport(fileName, it) } }
        this.footer = wrappedReports + listOfNotNull(report)
    }

    override fun close() {
        checkNotNull(footer) { "Footer not written" }
        // position of reports
        val footerStartPosition = output.position

        output.beginPrimitivO().use { o ->
            o.writeCollection(footer!!)
            // Total size = 8 + END_MAGIC_LENGTH
            o.writeLong(footerStartPosition)
            // Writing end-magic as a file integrity sign
            o.write(IOUtil.getEndMagicBytes())
        }

        output.close()
    }

    companion object {
        private const val MAGIC_V1 = "MiXCR.TREE.V01"
        const val MAGIC = MAGIC_V1
        const val MAGIC_LENGTH = 14
        val MAGIC_BYTES = MAGIC.toByteArray(StandardCharsets.US_ASCII)

        /**
         * Number of bytes in footer with meta information
         */
        val FOOTER_LENGTH = 8 + IOUtil.END_MAGIC_LENGTH

        const val shmFileExtension = "shmt"
    }
}
