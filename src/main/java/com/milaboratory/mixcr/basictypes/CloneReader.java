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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.VDJCGene;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CloneReader extends MiXCRFileInfo, AutoCloseable, ClonesSupplier {
    /**
     * Sequence of properties the stream is sorted by.
     *
     * @return sequence of properties the stream is sorted by
     */
    VDJCSProperties.CloneOrdering ordering();

    List<VDJCGene> getUsedGenes();

    @NotNull
    @Override
    default TagsInfo getTagsInfo() {
        return getHeader().getTagsInfo();
    }

    default VDJCAlignerParameters getAlignerParameters() {
        return getHeader().getAlignerParameters();
    }

    default CloneAssemblerParameters getAssemblerParameters() {
        return getHeader().getAssemblerParameters();
    }
}
