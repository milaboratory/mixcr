package com.milaboratory.mixcr.postanalysis;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.List;

/**
 *
 */
public interface SetPreprocessor<T> {
    SetPreprocessorSetup<T> nextSetupStep();

    MappingFunction<T> getMapper(int iDataset);

    /**
     * Returns statistics per dataset or null if the dataset was excluded as result of preprocessing
     */
    TIntObjectHashMap<List<SetPreprocessorStat>> getStat();

    /** Id from {@link SetPreprocessorFactory#id()} */
    String id();
}
