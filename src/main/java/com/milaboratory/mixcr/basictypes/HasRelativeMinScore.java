package com.milaboratory.mixcr.basictypes;

import io.repseq.core.GeneType;

public interface HasRelativeMinScore {
    float getRelativeMinScore(GeneType gt);
}
