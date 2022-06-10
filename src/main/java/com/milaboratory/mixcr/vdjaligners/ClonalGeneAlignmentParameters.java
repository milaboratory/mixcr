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

import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;

/**
 * Define common fields for alignment parameters for clones.
 *
 * ClonalGeneAlignmentParameters don't have some properties like target gene feature like {@link
 * GeneAlignmentParameters}.
 *
 * Created by poslavsky on 01/03/2017.
 */
public interface ClonalGeneAlignmentParameters {
    /**
     * Define score threshold for hits relative to top hit score.
     *
     * @return relative min score
     */
    float getRelativeMinScore();

    /**
     * Alignment scoring
     *
     * @return alignment scoring
     */
    AlignmentScoring<NucleotideSequence> getScoring();

    /**
     * Returns a copy of this object
     *
     * @return copy of this object
     */
    ClonalGeneAlignmentParameters clone();
}
