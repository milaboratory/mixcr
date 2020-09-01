package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.util.FilteredIterable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 *
 */
public class FilterPreprocessor<T> implements SetPreprocessor<T> {
    @JsonProperty("predicates")
    public final List<ElementPredicate<T>> predicates;

    @JsonCreator
    public FilterPreprocessor(@JsonProperty("predicates") List<ElementPredicate<T>> predicates) {
        this.predicates = predicates;
    }

    public FilterPreprocessor(ElementPredicate<T>... predicates) {
        this(Arrays.asList(predicates));
    }

    public FilterPreprocessor(ElementPredicate<T> predicate) {
        this(Collections.singletonList(predicate));
    }

    @Override
    public Function<Iterable<T>, Iterable<T>> setup(Iterable<T>[] sets) {
        return set -> new FilteredIterable<>(set, t -> predicates.stream().allMatch(s -> s.test(t)));
    }
}
