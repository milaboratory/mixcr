package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;

public class MutationsWithRange {
    private final NucleotideSequence sequence1;
    private final Mutations<NucleotideSequence> mutations;
    private final Range sequence1Range;

    public MutationsWithRange(NucleotideSequence sequence1, Mutations<NucleotideSequence> mutations, Range sequence1Range) {
        this.sequence1 = sequence1;
        this.mutations = mutations;
        this.sequence1Range = sequence1Range;
    }

    public NucleotideSequence getSequence1() {
        return sequence1;
    }

    public Mutations<NucleotideSequence> getMutations() {
        return mutations;
    }

    public Range getSequence1Range() {
        return sequence1Range;
    }
}
