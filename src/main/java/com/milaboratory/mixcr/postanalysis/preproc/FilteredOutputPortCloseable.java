package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.OutputPortCloseable;

import java.util.function.Predicate;

/**
 *
 */
public class FilteredOutputPortCloseable<T> implements OutputPortCloseable<T> {
    private final OutputPortCloseable<T> inner;
    private final Predicate<T> accept;

    public FilteredOutputPortCloseable(OutputPortCloseable<T> inner, Predicate<T> accept) {
        this.inner = inner;
        this.accept = accept;
    }

    @Override
    public void close() {
        inner.close();
    }

    @Override
    public T take() {
        while (true) {
            final T r = inner.take();
            if (r == null)
                return null;
            if (accept.test(r))
                return r;
        }
    }
}
