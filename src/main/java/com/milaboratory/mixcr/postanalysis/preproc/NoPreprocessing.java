package com.milaboratory.mixcr.postanalysis.preproc;


import com.milaboratory.mixcr.postanalysis.*;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Collections;
import java.util.List;

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

    @Override
    public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
        //noinspection ExternalizableWithoutPublicNoArgConstructor
        return new TIntObjectHashMap<List<SetPreprocessorStat>>() {
            @Override
            public List<SetPreprocessorStat> get(int key) {
                return Collections.emptyList();
            }
        };
    }

    @Override
    public String id() {
        return factory().id();
    }

    public static final Factory<?> factory = new Factory<>();

    @SuppressWarnings("unchecked")
    public static <T> Factory<T> factory() {return (Factory<T>) factory;}

    public static final class Factory<T> implements SetPreprocessorFactory<T> {
        @SuppressWarnings("unchecked")
        @Override
        public SetPreprocessor<T> newInstance() {
            return (SetPreprocessor<T>) NoPreprocessing.instance;
        }

        @Override
        public String id() {
            return "NoPreprocessing";
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
