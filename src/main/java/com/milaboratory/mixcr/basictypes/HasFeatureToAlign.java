/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

/**
 * Parameters that can return gene features used in alignments for different gene types.
 *
 * <p>Parent of parameters of assemblers and VDJ aligners.</p>
 */
public interface HasFeatureToAlign {
    GeneFeature getFeatureToAlign(GeneType geneType);
}
