package com.milaboratory.mixcr.postanalysis.overlap;

import com.milaboratory.mixcr.postanalysis.Dataset;

import java.util.List;

/**
 *
 */
public abstract class OverlapDataset<T> implements Dataset<OverlapGroup<T>> {
    public final List<String> datasetIds;

    public OverlapDataset(List<String> datasetIds) {
        this.datasetIds = datasetIds;
    }

    @Override
    public String id() {
        return String.join("_", datasetIds);
    }
}
