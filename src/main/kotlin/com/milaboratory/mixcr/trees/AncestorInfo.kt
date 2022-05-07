package com.milaboratory.mixcr.trees;

import com.milaboratory.core.sequence.NucleotideSequence;

public class AncestorInfo {
    private final NucleotideSequence sequence;
    private final int CDR3Begin;
    private final int CDR3End;

    public AncestorInfo(NucleotideSequence sequence, int CDR3Begin, int CDR3End) {
        this.sequence = sequence;
        this.CDR3Begin = CDR3Begin;
        this.CDR3End = CDR3End;
    }

    public NucleotideSequence getSequence() {
        return sequence;
    }

    public int getCDR3Begin() {
        return CDR3Begin;
    }

    public int getCDR3End() {
        return CDR3End;
    }
}
