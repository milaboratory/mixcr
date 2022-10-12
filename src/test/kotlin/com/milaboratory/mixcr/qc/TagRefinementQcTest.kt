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
package com.milaboratory.mixcr.qc

import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.qc.TagRefinementQc.tagRefinementQc
import io.repseq.core.VDJCLibraryRegistry
import org.junit.Test
import kotlin.io.path.Path

class TagRefinementQcTest {
    @Test
    internal fun name() {
        ClnsReader("/Users/poslavskysv/Downloads/clones.contigs.clns", VDJCLibraryRegistry.getDefault()).use { reader ->
            writePDF(Path("/Users/poslavskysv/Downloads/plt.pdf"), tagRefinementQc(reader, log = true))
        }
    }
}