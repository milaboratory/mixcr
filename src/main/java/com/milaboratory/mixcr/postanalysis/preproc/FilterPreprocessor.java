package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 */
public class FilterPreprocessor<T> implements SetPreprocessor<T> {
    final List<ElementPredicate<T>> predicates;
    final String id;
    final SetPreprocessorStat.Builder<T> stats;

    public FilterPreprocessor(List<ElementPredicate<T>> predicates,
                              WeightFunction<T> weightFunction,
                              String id) {
        this.predicates = predicates;
        this.id = id;
        this.stats = new SetPreprocessorStat.Builder<>(id, weightFunction);
    }

    @Override
    public SetPreprocessorSetup<T> nextSetupStep() {
        return null;
    }

    @Override
    public MappingFunction<T> getMapper(int iDataset) {
        return t -> {
            stats.before(iDataset, t);
            if (!predicates.stream().allMatch(p -> p.test(t)))
                return null;
            stats.after(iDataset, t);
            return t;
        };
    }

    @Override
    public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
        return stats.getStatMap();
    }

    @Override
    public String id() {
        return id;
    }

    public static final class Factory<T> implements SetPreprocessorFactory<T> {
        @JsonProperty("predicates")
        public final List<ElementPredicate<T>> predicates;
        @JsonProperty("weightFunction")
        public final WeightFunction<T> weightFunction;

        @JsonCreator
        public Factory(@JsonProperty("predicates") List<ElementPredicate<T>> predicates,
                       @JsonProperty("weightFunction") WeightFunction<T> weightFunction) {
            this.predicates = predicates;
            this.weightFunction = weightFunction;
        }

        @SafeVarargs
        public Factory(WeightFunction<T> weightFunction, ElementPredicate<T>... predicates) {
            this(Arrays.asList(predicates), weightFunction);
        }

        @Override
        public String id() {
            return "Filter " + predicates.stream()
                    .map(ElementPredicate::id)
                    .collect(Collectors.joining(", "));
        }

        @Override
        public SetPreprocessor<T> newInstance() {
            return new FilterPreprocessor<>(predicates, weightFunction, id());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Factory<?> factory = (Factory<?>) o;
            return Objects.equals(predicates, factory.predicates) && Objects.equals(weightFunction, factory.weightFunction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(predicates, weightFunction);
        }
    }
}
