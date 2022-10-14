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
import com.milaboratory.mixcr.basictypes.IOUtil.MAGIC_SHMT
import com.milaboratory.mixcr.basictypes.MiXCRFooter
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.primitivio.blocks.PrimitivOHybrid
import com.milaboratory.primitivio.writeCollection
import io.repseq.core.VDJCGene
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class SHMTreesWriter(
    private val output: PrimitivOHybrid
) : AutoCloseable {
    private var footer: MiXCRFooter? = null

    constructor(file: Path) : this(PrimitivOHybrid(file))

    fun writeHeader(
        originHeaders: List<MiXCRHeader>,
        header: MiXCRHeader,
        fileNames: List<String>,
        genes: List<VDJCGene>
    ) {
        output.beginPrimitivO(true).use { o ->
            // Writing magic bytes
            o.write(MAGIC_BYTES)

            // Writing version information
            o.writeUTF(MiXCRVersionInfo.get().getVersionString(AppVersionInfo.OutputType.ToFile))

            o.writeCollection(originHeaders)
            o.writeObject(header)
            o.writeCollection(fileNames)
            IOUtil.stdVDJCPrimitivOStateInit(o, genes, header.alignerParameters)
        }
    }

    /**
     * Must be closed by putting null
     */
    fun treesWriter(): InputPort<SHMTreeResult> = output.beginPrimitivOBlocks(3, 512)

    /**
     * Setting footer to be written on close
     */
    fun setFooter(footer: MiXCRFooter) {
        this.footer = footer
    }

    override fun close() {
        checkNotNull(footer) { "Footer not set" }

        // position of reports
        val footerStartPosition = output.position

        output.beginPrimitivO().use { o ->
            o.writeObject(footer)
            // Total size = 8 + END_MAGIC_LENGTH
            o.writeLong(footerStartPosition)
            // Writing end-magic as a file integrity sign
            o.write(IOUtil.getEndMagicBytes())
        }

        output.close()
    }

    companion object {
        const val MAGIC_V2 = "$MAGIC_SHMT.V02"
        const val MAGIC_V3 = "$MAGIC_SHMT.V03"
        const val MAGIC = MAGIC_V3
        const val MAGIC_LENGTH = 14
        val MAGIC_BYTES = MAGIC.toByteArray(StandardCharsets.US_ASCII)

        /**
         * Number of bytes in footer with meta information
         */
        val FOOTER_LENGTH = 8 + IOUtil.END_MAGIC_LENGTH

        const val shmFileExtension = "shmt"
    }
}
