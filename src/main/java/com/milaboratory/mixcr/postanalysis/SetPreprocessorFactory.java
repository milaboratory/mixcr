package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.InputPort;
import cc.redberry.pipe.OutputPortCloseable;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.preproc.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
@JsonAutoDetect(
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NoPreprocessing.Factory.class, name = "none"),
        @JsonSubTypes.Type(value = ClonesDownsamplingPreprocessorFactory.class, name = "downsample"),
        @JsonSubTypes.Type(value = OverlapPreprocessorAdapter.Factory.class, name = "overlap"),
        @JsonSubTypes.Type(value = PreprocessorChain.Factory.class, name = "chain"),
        @JsonSubTypes.Type(value = FilterPreprocessor.Factory.class, name = "filter"),
        @JsonSubTypes.Type(value = SelectTop.Factory.class, name = "selectTop"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface SetPreprocessorFactory<T> {
    SetPreprocessor<T> getInstance();

    default String[] description() {
        return new String[0];
    }

    @SuppressWarnings("unchecked")
    default SetPreprocessorFactory<T> filter(boolean before, ElementPredicate<T>... predicates) {
        FilterPreprocessor.Factory<T> filter = new FilterPreprocessor.Factory<>(Arrays.asList(predicates));
        if (this instanceof PreprocessorChain.Factory) {
            PreprocessorChain.Factory<T> p = (PreprocessorChain.Factory<T>) this;
            List<SetPreprocessorFactory<T>> list = new ArrayList<>();
            if (before) {
                list.add(filter);
                list.addAll(p.chain);
            } else {
                list.addAll(p.chain);
                list.add(filter);
            }
            return new PreprocessorChain.Factory<>(list);
        } else
            return before
                    ? new PreprocessorChain.Factory<>(filter, this)
                    : new PreprocessorChain.Factory<>(this, filter);
    }

    default SetPreprocessorFactory<T> filterAfter(ElementPredicate<T>... predicates) {
        return filter(false, predicates);
    }

    default SetPreprocessorFactory<T> filterFirst(ElementPredicate<T>... predicates) {
        return filter(true, predicates);
    }

    @SuppressWarnings("unchecked")
    default SetPreprocessorFactory<T> filterAfter(List<ElementPredicate<T>> predicates) {
        return filter(false, predicates.toArray(new ElementPredicate[0]));
    }

    @SuppressWarnings("unchecked")
    default SetPreprocessorFactory<T> filterFirst(List<ElementPredicate<T>> predicates) {
        return filter(true, predicates.toArray(new ElementPredicate[0]));
    }

    @SuppressWarnings("unchecked")
    static <T> Dataset<T>[] processDatasets(SetPreprocessor<T> proc, Dataset<T>... initial) {
        while (true) {
            SetPreprocessorSetup<T> setup = proc.nextSetupStep();
            if (setup == null) {
                Dataset<T>[] result = new Dataset[initial.length];
                for (int i = 0; i < initial.length; i++) {
                    int datasetIdx = i;
                    MappingFunction<T> mapper = proc.getMapper(datasetIdx);
                    result[i] = new Dataset<T>() {
                        @Override
                        public String id() {
                            return initial[datasetIdx].id();
                        }

                        @Override
                        public OutputPortCloseable<T> mkElementsPort() {
                            OutputPortCloseable<T> inner = initial[datasetIdx].mkElementsPort();
                            return new OutputPortCloseable<T>() {
                                @Override
                                public void close() {
                                    inner.close();
                                }

                                @Override
                                public T take() {
                                    while (true) {
                                        T t = inner.take();
                                        if (t == null)
                                            return null;

                                        T r = mapper.apply(t);
                                        if (r == null)
                                            continue;
                                        return r;
                                    }
                                }
                            };
                        }
                    };
                }
                return result;
            }
            setup.initialize(initial.length);

            List<InputPort<T>> consumers = IntStream.range(0, initial.length)
                    .mapToObj(setup::consumer)
                    .collect(Collectors.toList());

            for (int i = 0; i < initial.length; i++) {
                try (OutputPortCloseable<T> port = initial[i].mkElementsPort()) {
                    for (T t : CUtils.it(port)) {
                        consumers.get(i).put(t);
                    }
                }
            }
            for (InputPort<T> c : consumers) {
                c.put(null);
            }
        }
    }
}
