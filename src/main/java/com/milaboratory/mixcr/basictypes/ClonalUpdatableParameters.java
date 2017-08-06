package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.vdjaligners.ClonalGeneAlignmentParameters;

/**
 * Created by poslavsky on 01/03/2017.
 */
public interface ClonalUpdatableParameters {
    /**
     * Set absent parameters from ClonalGeneAlignmentParameters object
     *
     * @param alignerParameters
     */
    void updateFrom(ClonalGeneAlignmentParameters alignerParameters);

    /**
     * Returns true if all parameters are defined
     */
    boolean isComplete();
}
