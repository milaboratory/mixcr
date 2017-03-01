package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.vdjaligners.ClonalGeneAlignmentParameters;

/**
 * Created by poslavsky on 01/03/2017.
 */
public interface ClonalUpdatableParameters {
    void updateFrom(ClonalGeneAlignmentParameters alignerParameters);

    boolean isComplete();
}
