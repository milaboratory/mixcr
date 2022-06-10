/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.postanalysis.overlap;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class OverlapGroup<T> implements Iterable<List<T>> {
    /**
     * Elements in group separated by sample
     */
    public final List<List<T>> elements;

    public OverlapGroup(List<List<T>> elements) {
        this.elements = elements;
    }

    public int size() {
        return elements.size();
    }

    public List<T> getBySample(int sampleIndex) {
        return elements.get(sampleIndex);
    }

    public boolean isEmpty() {
        return elements.stream().allMatch(List::isEmpty);
    }

    public boolean notEmpty() {
        return !isEmpty();
    }

    @Override
    public Iterator<List<T>> iterator() {
        return elements.iterator();
    }

    public Stream<List<T>> stream() {
        return elements.stream();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OverlapGroup<?> that = (OverlapGroup<?>) o;
        return Objects.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements);
    }
}
