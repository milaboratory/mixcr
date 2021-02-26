package com.milaboratory.mixcr.postanalysis.preproc;


import com.milaboratory.mixcr.postanalysis.MappingFunction;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSetup;

/**
 *
 */
public class NoPreprocessing<T> implements SetPreprocessor<T> {
    private static final NoPreprocessing<?> instance = new NoPreprocessing<>();

    private NoPreprocessing() {}

    @Override
    public SetPreprocessorSetup<T> nextSetupStep() {
        return null;
    }

    @Override
    public MappingFunction<T> getMapper(int iDataset) {
        return MappingFunction.identity();
    }

    public static final Factory<?> factory = new Factory<>();

    @SuppressWarnings("unchecked")
    public static <T> Factory<T> factory() { return (Factory<T>) factory; }

    public static final class Factory<T> implements SetPreprocessorFactory<T> {
        @SuppressWarnings("unchecked")
        @Override
        public SetPreprocessor<T> getInstance() {
            return (SetPreprocessor<T>) NoPreprocessing.instance;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o != null && getClass() == o.getClass();
        }

        @Override
        public int hashCode() {
            return "NoPreprocessing".hashCode();
        }
    }
}
