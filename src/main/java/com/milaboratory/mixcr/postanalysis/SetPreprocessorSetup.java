package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.InputPort;

/**
 *
 */
public interface SetPreprocessorSetup<T> {
    void initialize(int nDatasets);

    InputPort<T> consumer(int iDataset);
}
