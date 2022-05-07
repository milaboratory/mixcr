package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.List;

class MutationsFromVJGermline {
    private final List<MutationsWithRange> VMutationsWithoutCDR3;
    private final MutationsWithRange VMutationsInCDR3WithoutNDN;
    private final Pair<Mutations<NucleotideSequence>, Range> knownVMutationsWithinNDN;
    private final NucleotideSequence knownNDN;
    private final Pair<Mutations<NucleotideSequence>, Range> knownJMutationsWithinNDN;
    private final MutationsWithRange JMutationsInCDR3WithoutNDN;
    private final List<MutationsWithRange> JMutationsWithoutCDR3;

    public MutationsFromVJGermline(
            List<MutationsWithRange> VMutationsWithoutCDR3,
            MutationsWithRange VMutationsInCDR3WithoutNDN,
            Pair<Mutations<NucleotideSequence>, Range> knownVMutationsWithinNDN,
            NucleotideSequence knownNDN,
            Pair<Mutations<NucleotideSequence>, Range> knownJMutationsWithinNDN,
            MutationsWithRange JMutationsInCDR3WithoutNDN,
            List<MutationsWithRange> JMutationsWithoutCDR3
    ) {
        this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
        this.VMutationsInCDR3WithoutNDN = VMutationsInCDR3WithoutNDN;
        this.knownVMutationsWithinNDN = knownVMutationsWithinNDN;
        this.knownNDN = knownNDN;
        this.knownJMutationsWithinNDN = knownJMutationsWithinNDN;
        this.JMutationsInCDR3WithoutNDN = JMutationsInCDR3WithoutNDN;
        this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
    }

    public int getVJMutationsCount() {
        return VMutationsWithoutCDR3.stream().mapToInt(MutationsWithRange::mutationsCount).sum() +
                VMutationsInCDR3WithoutNDN.mutationsCount() +
                JMutationsWithoutCDR3.stream().mapToInt(MutationsWithRange::mutationsCount).sum() +
                JMutationsInCDR3WithoutNDN.mutationsCount();
    }

    public Pair<Mutations<NucleotideSequence>, Range> getKnownVMutationsWithinNDN() {
        return knownVMutationsWithinNDN;
    }

    public NucleotideSequence getKnownNDN() {
        return knownNDN;
    }

    public Pair<Mutations<NucleotideSequence>, Range> getKnownJMutationsWithinNDN() {
        return knownJMutationsWithinNDN;
    }

    public List<MutationsWithRange> getVMutationsWithoutCDR3() {
        return VMutationsWithoutCDR3;
    }

    public MutationsWithRange getVMutationsInCDR3WithoutNDN() {
        return VMutationsInCDR3WithoutNDN;
    }

    public List<MutationsWithRange> getVMutations() {
        List<MutationsWithRange> result = new ArrayList<>(VMutationsWithoutCDR3);
        result.add(VMutationsInCDR3WithoutNDN);
        return result;
    }

    public List<MutationsWithRange> getJMutationsWithoutCDR3() {
        return JMutationsWithoutCDR3;
    }

    public MutationsWithRange getJMutationsInCDR3WithoutNDN() {
        return JMutationsInCDR3WithoutNDN;
    }

    public List<MutationsWithRange> getJMutations() {
        List<MutationsWithRange> result = new ArrayList<>();
        result.add(JMutationsInCDR3WithoutNDN);
        result.addAll(JMutationsWithoutCDR3);
        return result;
    }
}
