/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
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
