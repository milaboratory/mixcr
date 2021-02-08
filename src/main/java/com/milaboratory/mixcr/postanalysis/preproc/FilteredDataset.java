package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.postanalysis.Dataset;

import java.util.function.Predicate;

/**
 *
 */
public class FilteredDataset<T> implements Dataset<T> {
    private final Dataset<T> inner;
    private final Predicate<T> accept;

    public FilteredDataset(Dataset<T> inner, Predicate<T> accept) {
        this.inner = inner;
        this.accept = accept;
    }

    @Override
    public String id() {
        return inner.id();
    }

    @Override
    public OutputPortCloseable<T> mkElementsPort() {
        return new FilteredOutputPortCloseable<>(inner.mkElementsPort(), accept);
    }
}
