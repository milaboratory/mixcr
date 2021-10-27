package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;

/**
 *
 */
public class CloneWrapper {
    /** Original clonotype */
    public final Clone clone;
    /** Dataset serial number */
    public final int datasetId;

    public CloneWrapper(Clone clone, int datasetId) {
        this.clone = clone;
        this.datasetId = datasetId;
    }
}
