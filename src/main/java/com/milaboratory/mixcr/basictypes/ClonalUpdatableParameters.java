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
