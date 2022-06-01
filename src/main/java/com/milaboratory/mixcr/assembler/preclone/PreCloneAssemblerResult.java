package com.milaboratory.mixcr.assembler.preclone;

import java.util.List;

public final class PreCloneAssemblerResult {
    private final List<PreClone> clones;
    private final long[] alignmentToClone;

    public PreCloneAssemblerResult(List<PreClone> clones, long[] alignmentToClone) {
        this.clones = clones;
        this.alignmentToClone = alignmentToClone;
    }

    public List<PreClone> getClones() {
        return clones;
    }

    public long getCloneForAlignment(int localAlignmentId){
        return alignmentToClone == null ? -1 : alignmentToClone[localAlignmentId];
    }
}
