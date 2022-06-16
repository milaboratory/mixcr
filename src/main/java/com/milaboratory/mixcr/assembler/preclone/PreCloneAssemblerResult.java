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
package com.milaboratory.mixcr.assembler.preclone;

import java.util.List;

public final class PreCloneAssemblerResult {
    private final List<PreCloneImpl> clones;
    private final long[] alignmentToClone;

    public PreCloneAssemblerResult(List<PreCloneImpl> clones, long[] alignmentToClone) {
        this.clones = clones;
        this.alignmentToClone = alignmentToClone;
    }

    public List<PreCloneImpl> getClones() {
        return clones;
    }

    public long getCloneForAlignment(int localAlignmentId){
        return alignmentToClone == null ? -1 : alignmentToClone[localAlignmentId];
    }
}
