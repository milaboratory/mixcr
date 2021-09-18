/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
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
