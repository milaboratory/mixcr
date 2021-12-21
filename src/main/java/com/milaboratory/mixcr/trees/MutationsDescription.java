package com.milaboratory.mixcr.trees;

import java.util.List;

class MutationsDescription {
    private final List<MutationsWithRange> VMutationsWithoutNDN;
    private final MutationsWithRange knownNDN;
    private final List<MutationsWithRange> JMutationsWithoutNDN;

    MutationsDescription(
            List<MutationsWithRange> VMutationsWithoutNDN,
            MutationsWithRange knownNDN,
            List<MutationsWithRange> JMutationsWithoutNDN
    ) {
        this.knownNDN = knownNDN;
        this.VMutationsWithoutNDN = VMutationsWithoutNDN;
        this.JMutationsWithoutNDN = JMutationsWithoutNDN;
    }

    public List<MutationsWithRange> getVMutationsWithoutNDN() {
        return VMutationsWithoutNDN;
    }

    public MutationsWithRange getKnownNDN() {
        return knownNDN;
    }

    public List<MutationsWithRange> getJMutationsWithoutNDN() {
        return JMutationsWithoutNDN;
    }
}
