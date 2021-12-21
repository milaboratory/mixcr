package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NucleotideSequence;

class RootInfo {
    private final Range VRangeInCDR3;
    private final Range JRangeInCDR3;
    private final NucleotideSequence reconstructedNDN;

    RootInfo(Range VRangeInCDR3, NucleotideSequence reconstructedNDN, Range JRangeInCDR3) {
        this.VRangeInCDR3 = VRangeInCDR3;
        this.JRangeInCDR3 = JRangeInCDR3;
        this.reconstructedNDN = reconstructedNDN;
    }

    public Range getVRangeInCDR3() {
        return VRangeInCDR3;
    }

    public Range getJRangeInCDR3() {
        return JRangeInCDR3;
    }

    public NucleotideSequence getReconstructedNDN() {
        return reconstructedNDN;
    }
}
