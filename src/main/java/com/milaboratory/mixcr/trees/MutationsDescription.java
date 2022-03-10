package com.milaboratory.mixcr.trees;

import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;

import java.util.List;

class MutationsDescription {
    private final List<MutationsWithRange> VMutationsWithoutCDR3;
    private final MutationsWithRange VMutationsInCDR3WithoutNDN;
    private final MutationsWithRange knownNDN;
    private final MutationsWithRange JMutationsInCDR3WithoutNDN;
    private final List<MutationsWithRange> JMutationsWithoutCDR3;

    public MutationsDescription(
            List<MutationsWithRange> VMutationsWithoutCDR3,
            MutationsWithRange VMutationsInCDR3WithoutNDN,
            MutationsWithRange knownNDN,
            MutationsWithRange JMutationsInCDR3WithoutNDN,
            List<MutationsWithRange> JMutationsWithoutCDR3
    ) {
        this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
        this.VMutationsInCDR3WithoutNDN = VMutationsInCDR3WithoutNDN;
        this.knownNDN = knownNDN;
        this.JMutationsInCDR3WithoutNDN = JMutationsInCDR3WithoutNDN;
        this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
    }

    public List<MutationsWithRange> getVMutationsWithoutCDR3() {
        return VMutationsWithoutCDR3;
    }

    public MutationsWithRange getVMutationsInCDR3WithoutNDN() {
        return VMutationsInCDR3WithoutNDN;
    }

    public MutationsWithRange getKnownNDN() {
        return knownNDN;
    }

    public MutationsWithRange getJMutationsInCDR3WithoutNDN() {
        return JMutationsInCDR3WithoutNDN;
    }

    public List<MutationsWithRange> getJMutationsWithoutCDR3() {
        return JMutationsWithoutCDR3;
    }

    public MutationsDescription withKnownNDNMutations(MutationsWithRange mutations) {
        return new MutationsDescription(
                VMutationsWithoutCDR3,
                VMutationsInCDR3WithoutNDN,
                mutations,
                JMutationsInCDR3WithoutNDN,
                JMutationsWithoutCDR3
        );
    }

    Mutations<NucleotideSequence> combinedVMutations() {
        boolean allBasedOnTheSameSequence = getVMutationsWithoutCDR3().stream()
                .map(MutationsWithRange::getSequence1)
                .allMatch(getVMutationsInCDR3WithoutNDN().getSequence1()::equals);
        if (!allBasedOnTheSameSequence) {
            throw new IllegalArgumentException();
        }
        MutationsBuilder<NucleotideSequence> mutationsFromRootToBaseBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        getVMutationsWithoutCDR3().stream()
                .map(MutationsWithRange::mutationsForRange)
                .forEach(mutationsFromRootToBaseBuilder::append);
        mutationsFromRootToBaseBuilder.append(getVMutationsInCDR3WithoutNDN().mutationsForRange());
        return mutationsFromRootToBaseBuilder.createAndDestroy();
    }

    Mutations<NucleotideSequence> combinedJMutations() {
        boolean allBasedOnTheSameSequence = getJMutationsWithoutCDR3().stream()
                .map(MutationsWithRange::getSequence1)
                .allMatch(getJMutationsInCDR3WithoutNDN().getSequence1()::equals);
        if (!allBasedOnTheSameSequence) {
            throw new IllegalArgumentException();
        }
        MutationsBuilder<NucleotideSequence> mutationsFromRootToBaseBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        mutationsFromRootToBaseBuilder.append(getJMutationsInCDR3WithoutNDN().mutationsForRange());
        getJMutationsWithoutCDR3().stream()
                .map(MutationsWithRange::mutationsForRange)
                .forEach(mutationsFromRootToBaseBuilder::append);
        return mutationsFromRootToBaseBuilder.createAndDestroy();
    }
}
