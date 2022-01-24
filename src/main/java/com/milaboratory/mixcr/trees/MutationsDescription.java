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

    public Mutations<NucleotideSequence> getConcatenatedVMutations() {
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);

        VMutationsWithoutCDR3.stream()
                .map(MutationsWithRange::mutationsForRange)
                .forEach(builder::append);
        builder.append(VMutationsInCDR3WithoutNDN.mutationsForRange());
        return builder.createAndDestroy();
    }

    public Mutations<NucleotideSequence> getConcatenatedJMutations() {
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);

        builder.append(JMutationsInCDR3WithoutNDN.mutationsForRange());
        JMutationsWithoutCDR3.stream()
                .map(MutationsWithRange::mutationsForRange)
                .forEach(builder::append);
        return builder.createAndDestroy();
    }

}
