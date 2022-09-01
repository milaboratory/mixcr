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
    fun header(reader: VDJCAlignmentsReader?)
    fun header(info: MiXCRMetaInfo?, genes: List<VDJCGene?>?)
    fun write(alignment: VDJCAlignments?)

    object DummyWriter : VDJCAlignmentsWriterI {
        override fun setNumberOfProcessedReads(numberOfProcessedReads: Long) {}
        override fun header(reader: VDJCAlignmentsReader?) {}
        override fun header(info: MiXCRMetaInfo?, genes: List<VDJCGene?>?) {}
        override fun write(alignment: VDJCAlignments?) {}
        override fun close() {}
    }
}
