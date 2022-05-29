package com.milaboratory.mixcr.assembler.preclone;

import gnu.trove.set.hash.TIntHashSet;

public final class PreCloneWithAlignments {
    /** Assembled pre-clone */
    public final PreClone preClone;
    /** Group-local indices of alignments assigned to this pre-clone */
    public final TIntHashSet alignments;

    public PreCloneWithAlignments(PreClone preClone, TIntHashSet alignments) {
        this.preClone = preClone;
        this.alignments = alignments;
    }


}
