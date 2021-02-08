package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    @Override
    public String[] description() {
        return predicates.stream()
                .map(ElementPredicate::description)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .map(f -> "Filter: " + f)
                .toArray(String[]::new);
    }

    public FilterPreprocessor(ElementPredicate<T>... predicates) {
        this(Arrays.asList(predicates));
    }

    public FilterPreprocessor(ElementPredicate<T> predicate) {
        this(Collections.singletonList(predicate));
    }

    @Override
    public Function<Dataset<T>, Dataset<T>> setup(Dataset<T>[] sets) {
        return set -> new FilteredDataset<>(set, t -> predicates.stream().allMatch(s -> s.test(t)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterPreprocessor<?> that = (FilterPreprocessor<?>) o;
        return Objects.equals(predicates, that.predicates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicates);
    }
}
