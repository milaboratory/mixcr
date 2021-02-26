package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.MappingFunction;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSetup;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public class FilterPreprocessor<T> implements SetPreprocessor<T> {
    final List<ElementPredicate<T>> predicates;

    FilterPreprocessor(@JsonProperty("predicates") List<ElementPredicate<T>> predicates) {
        this.predicates = predicates;
    }

    @Override
    public SetPreprocessorSetup<T> nextSetupStep() {
        return null;
    }

    @Override
    public MappingFunction<T> getMapper(int iDataset) {
        return t -> predicates.stream().allMatch(p -> p.test(t)) ? t : null;
    }

    public static final class Factory<T> implements SetPreprocessorFactory<T> {
        @JsonProperty("predicates")
        public final List<ElementPredicate<T>> predicates;

        @JsonCreator
        public Factory(@JsonProperty("predicates") List<ElementPredicate<T>> predicates) {
            this.predicates = predicates;
        }

        public Factory(ElementPredicate<T>... predicates) {
            this(Arrays.asList(predicates));
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

        @Override
        public SetPreprocessor<T> getInstance() {
            return new FilterPreprocessor<>(predicates);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Factory<?> that = (Factory<?>) o;
            return Objects.equals(predicates, that.predicates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(predicates);
        }
    }
}
