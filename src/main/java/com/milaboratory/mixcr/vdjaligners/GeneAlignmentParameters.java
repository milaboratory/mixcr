/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
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
