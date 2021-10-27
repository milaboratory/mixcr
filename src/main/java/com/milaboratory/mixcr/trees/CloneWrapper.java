package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;

/**
 *
 */
public class CloneWrapper {
    /**
     *
     */
    public final Clone clone;
    /**
     *
     */
    public final int datasetId;

    public CloneWrapper(Clone clone, int datasetId) {
        this.clone = clone;
        this.datasetId = datasetId;
    }
}
