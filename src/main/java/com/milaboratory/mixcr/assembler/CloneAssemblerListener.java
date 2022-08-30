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
package com.milaboratory.mixcr.assembler;

import com.milaboratory.mixcr.assembler.preclone.PreClone;

public interface CloneAssemblerListener {
    /* Initial Assembly */

    void onTooShortClonalSequence(PreClone preClone);

    void onNewCloneCreated(CloneAccumulator accumulator);

    void onTooManyLowQualityPoints(PreClone preClone);

    void onAlignmentDeferred(PreClone preClone);

    void onAlignmentAddedToClone(PreClone preClone, CloneAccumulator accumulator);

    /* Mapping */

    void onNoCandidateFoundForDeferredAlignment(PreClone preClone);

    void onDeferredAlignmentMappedToClone(PreClone preClone, CloneAccumulator accumulator);

    /* Clustering */

    void onClustered(CloneAccumulator majorClone, CloneAccumulator minorClone, boolean countAdded);

    void onPreClustered(CloneAccumulator majorClone, CloneAccumulator minorClone);

    void onCloneDropped(CloneAccumulator clone);
}
