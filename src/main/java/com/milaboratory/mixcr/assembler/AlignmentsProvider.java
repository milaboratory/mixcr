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
package com.milaboratory.mixcr.assembler;

import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.util.OutputPortWithProgress;

public interface AlignmentsProvider extends AutoCloseable {
    /** Creates new alignments reader */
    OutputPortWithProgress<VDJCAlignments> readAlignments();

    /**
     * Should return total number of reads (not alignments) after whole analysis if such information available.
     *
     * @return total number of reads
     */
    long getNumberOfReads();

    // this override strips out exceptions form the method signature (see raw AutoCloseable for comparison)
    void close();
}
