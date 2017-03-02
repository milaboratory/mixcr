package com.milaboratory.mixcr.vdjaligners;

import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

/**
 * Define additional properties required to align raw sequencing reads.
 *
 * Created by poslavsky on 01/03/2017.
 */
public interface GeneAlignmentParameters extends ClonalGeneAlignmentParameters {
    /**
     * Part of gene to use as alignment subject (sequence1 in terms of MiLib)
     */
    GeneFeature getGeneFeatureToAlign();

    /**
     * getGeneFeatureToAlign().getGeneType()
     */
    GeneType getGeneType();

    /**
     * Return copy of this object
     */
    @Override
    GeneAlignmentParameters clone();
}
