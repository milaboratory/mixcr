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
package com.milaboratory.mixcr.basictypes

import io.repseq.core.VDJCGene

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
interface VDJCAlignmentsWriterI : AutoCloseable {
    fun setNumberOfProcessedReads(numberOfProcessedReads: Long)
    fun inheritHeaderAndFooterFrom(reader: VDJCAlignmentsReader)
    fun writeHeader(info: MiXCRHeader, genes: List<VDJCGene>)
    fun setFooter(footer: MiXCRFooter)
    fun write(alignment: VDJCAlignments?)

    object DummyWriter : VDJCAlignmentsWriterI {
        override fun setNumberOfProcessedReads(numberOfProcessedReads: Long) {}
        override fun inheritHeaderAndFooterFrom(reader: VDJCAlignmentsReader) {}
        override fun writeHeader(info: MiXCRHeader, genes: List<VDJCGene>) {}
        override fun write(alignment: VDJCAlignments?) {}
        override fun setFooter(footer: MiXCRFooter) {}
        override fun close() {}
    }
}
