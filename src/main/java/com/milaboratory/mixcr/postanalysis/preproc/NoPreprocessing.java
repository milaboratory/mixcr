package com.milaboratory.mixcr.postanalysis.preproc;

import com.milaboratory.mixcr.postanalysis.SetPreprocessor;

import java.util.function.Function;

/**
 *
 */
public class NoPreprocessing<T> implements SetPreprocessor<T> {
    public static final NoPreprocessing<?> INSTANCE = new NoPreprocessing<>();

    @Override
    public String[] description() {
        return new String[0];
    }

    @Override
    public Function<Iterable<T>, Iterable<T>> setup(Iterable<T>[] sets) {
        return set -> set;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 17;
    }
}
