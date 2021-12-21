package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.apache.commons.math3.util.Pair;

import java.util.List;

class MutationsFromVJGermline {
    private final List<MutationsWithRange> VMutationsWithoutNDN;
    private final Pair<Mutations<NucleotideSequence>, Range> knownVMutationsWithinNDN;
    private final Range VRangeInCDR3;
    private final Range JRangeInCDR3;
    private final NucleotideSequence knownNDN;
    private final List<MutationsWithRange> JMutationsWithoutNDN;
    private final Pair<Mutations<NucleotideSequence>, Range> knownJMutationsWithinNDN;

    MutationsFromVJGermline(
            List<MutationsWithRange> VMutationsWithoutNDN,
            Pair<Mutations<NucleotideSequence>, Range> knownVMutationsWithinNDN,
            Range VRangeInCDR3,
            Range JRangeInCDR3,
            NucleotideSequence knownNDN,
            List<MutationsWithRange> JMutationsWithoutNDN,
            Pair<Mutations<NucleotideSequence>, Range> knownJMutationsWithinNDN
    ) {
        this.VMutationsWithoutNDN = VMutationsWithoutNDN;
        this.knownVMutationsWithinNDN = knownVMutationsWithinNDN;
        this.VRangeInCDR3 = VRangeInCDR3;
        this.JRangeInCDR3 = JRangeInCDR3;
        this.JMutationsWithoutNDN = JMutationsWithoutNDN;
        this.knownNDN = knownNDN;
        this.knownJMutationsWithinNDN = knownJMutationsWithinNDN;
    }

    public List<MutationsWithRange> getVMutationsWithoutNDN() {
        return VMutationsWithoutNDN;
    }

    public Pair<Mutations<NucleotideSequence>, Range> getKnownVMutationsWithinNDN() {
        return knownVMutationsWithinNDN;
    }

    public Range getVRangeInCDR3() {
        return VRangeInCDR3;
    }

    public Range getJRangeInCDR3() {
        return JRangeInCDR3;
    }

    public NucleotideSequence getKnownNDN() {
        return knownNDN;
    }

    public List<MutationsWithRange> getJMutationsWithoutNDN() {
        return JMutationsWithoutNDN;
    }

    public Pair<Mutations<NucleotideSequence>, Range> getKnownJMutationsWithinNDN() {
        return knownJMutationsWithinNDN;
    }
}
