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

    public int nSamples() {
        return datasetIds.size();
    }

    @Override
    public String id() {
        return "overlap";
    }
}
