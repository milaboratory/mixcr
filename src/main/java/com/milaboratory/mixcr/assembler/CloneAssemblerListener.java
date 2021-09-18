/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.milaboratory.mixcr.basictypes.VDJCAlignments;

public interface CloneAssemblerListener {
    /* Initial Assembly */

    void onNewCloneCreated(CloneAccumulator accumulator);

    void onFailedToExtractTarget(VDJCAlignments alignments);

    void onTooManyLowQualityPoints(VDJCAlignments alignments);

    void onAlignmentDeferred(VDJCAlignments alignments);

    void onAlignmentAddedToClone(VDJCAlignments alignments, CloneAccumulator accumulator);

    /* Mapping */

    void onNoCandidateFoundForDeferredAlignment(VDJCAlignments alignments);

    void onDeferredAlignmentMappedToClone(VDJCAlignments alignments, CloneAccumulator accumulator);

    /* Clustering */

    void onClustered(CloneAccumulator majorClone, CloneAccumulator minorClone, boolean countAdded);

    void onPreClustered(CloneAccumulator majorClone, CloneAccumulator minorClone);

    void onCloneDropped(CloneAccumulator clone);
}
