package com.milaboratory.mixcr.postanalysis;

/**
 *
 */
public interface SetPreprocessor<T> {
    SetPreprocessorSetup<T> nextSetupStep();

    MappingFunction<T> getMapper(int iDataset);

    default SetPreprocessorStat getStat() {
        return null;
    }
}
