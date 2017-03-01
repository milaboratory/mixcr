package com.milaboratory.mixcr.vdjaligners;

import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

/**
 * Created by poslavsky on 01/03/2017.
 */
public interface GeneAlignmentParameters extends ClonalGeneAlignmentParameters {
    GeneFeature getGeneFeatureToAlign();

    GeneType getGeneType();

    @Override
    GeneAlignmentParameters clone();
}
