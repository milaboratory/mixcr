/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.vdjaligners;

public enum VDJCAlignmentFailCause {
    NoHits("Alignment failed, no hits (not TCR/IG?)"),
    NoCDR3Parts("Alignment failed because of absence of CDR3 parts"),
    NoVHits("Alignment failed because of absence of V hits"),
    NoJHits("Alignment failed because of absence of J hits"),
    VAndJOnDifferentTargets("No target with both V and J alignments"),
    LowTotalScore("Alignment failed because of low total score");
    public final String reportLine;

    VDJCAlignmentFailCause(String reportLine) {
        this.reportLine = reportLine;
    }
}
