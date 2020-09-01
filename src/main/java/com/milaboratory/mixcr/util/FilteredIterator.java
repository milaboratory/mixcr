package com.milaboratory.mixcr.util;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 *
 */
public class FilteredIterator<T> implements Iterator<T> {
    private final Iterator<T> inner;
    private final Predicate<T> accept;

    public FilteredIterator(Iterator<T> inner, Predicate<T> accept) {
        this.inner = inner;
        this.accept = accept;
    }

    private T ref = null;

    @Override
    public boolean hasNext() {
        if (ref != null)
            return true;
        if (!inner.hasNext())
            return false;
        ref = inner.next();
        while (!accept.test(ref)) {
            if (!inner.hasNext())
                return false;
            ref = inner.next();
        }
        return true;
    }

    @Override
    public T next() {
        T r = this.ref;
        this.ref = null;
        return r;
    }
}
