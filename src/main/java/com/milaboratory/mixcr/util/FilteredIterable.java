package com.milaboratory.mixcr.util;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 *
 */
public class FilteredIterable<T> implements Iterable<T> {
    private final Iterable<T> inner;
    private final Predicate<T> accept;

    public FilteredIterable(Iterable<T> inner, Predicate<T> accept) {
        this.inner = inner;
        this.accept = accept;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return new FilteredIterator<>(inner.iterator(), accept);
    }
}
