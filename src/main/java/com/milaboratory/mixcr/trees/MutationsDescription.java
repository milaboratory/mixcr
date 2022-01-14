package com.milaboratory.mixcr.trees;

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
}
