package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NucleotideSequence;

public class RootInfo {
    private final Range VRangeInCDR3;
    private final Range JRangeInCDR3;
    private final VJBase VJBase;
    private final NucleotideSequence reconstructedNDN;

    RootInfo(Range VRangeInCDR3, NucleotideSequence reconstructedNDN, Range JRangeInCDR3, VJBase vjBase) {
        this.VRangeInCDR3 = VRangeInCDR3;
        this.JRangeInCDR3 = JRangeInCDR3;
        this.reconstructedNDN = reconstructedNDN;
        VJBase = vjBase;
    }

    public VJBase getVJBase() {
        return VJBase;
    }

    public Range getVRangeInCDR3() {
        return VRangeInCDR3;
    }

    public Range getJRangeInCDR3() {
        return JRangeInCDR3;
    }

    NucleotideSequence getReconstructedNDN() {
        return reconstructedNDN;
    }

    @Override
    public String toString() {
        return "RootInfo{" +
                "VRangeInCDR3=" + VRangeInCDR3 +
                ", JRangeInCDR3=" + JRangeInCDR3 +
                ", reconstructedNDN=" + reconstructedNDN +
                '}';
    }
}
