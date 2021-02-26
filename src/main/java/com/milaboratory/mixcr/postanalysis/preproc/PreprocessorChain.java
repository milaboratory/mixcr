package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.InputPort;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.MappingFunction;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSetup;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class PreprocessorChain<T> implements SetPreprocessor<T> {
    final List<SetPreprocessor<T>> chain;

    public PreprocessorChain(List<SetPreprocessor<T>> chain) {
        this.chain = chain;
    }

    private final AtomicReference<Function<Integer, MappingFunction<T>>> mapperRef = new AtomicReference<>(i -> t -> t);
    private int lastActive = 0;

    @Override
    public SetPreprocessorSetup<T> nextSetupStep() {
        SetPreprocessorSetup<T> innerStep = null;
        for (int i = lastActive; i < chain.size(); i++) {
            SetPreprocessor<T> p = chain.get(i);
            SetPreprocessorSetup<T> step = p.nextSetupStep();
            if (step != null) {
                lastActive = i;
                innerStep = step;
                break;
            } else {
                if (mapperRef.get() == null)
                    mapperRef.set(p::getMapper);
                else {
                    Function<Integer, MappingFunction<T>> mapper = mapperRef.get();
                    Function<Integer, MappingFunction<T>> newMapper = iDataset -> {
                        MappingFunction<T> prevMapper = mapper.apply(iDataset);
                        MappingFunction<T> nextMapper = p.getMapper(iDataset);

                        return t -> {
                            if (t == null)
                                return null;
                            T r = prevMapper.apply(t);
                            if (r == null)
                                return null;
                            return nextMapper.apply(r);
                        };
                    };
                    mapperRef.set(newMapper);
                }
            }
        }

        Function<Integer, MappingFunction<T>> mapper = mapperRef.get();
        if (innerStep != null) {
            SetPreprocessorSetup<T> _innerSetup = innerStep;
            return new SetPreprocessorSetup<T>() {
                boolean initialized = false;

                @Override
                public void initialize(int nDatasets) {
                    if (initialized)
                        throw new IllegalStateException();
                    initialized = true;
                    _innerSetup.initialize(nDatasets);
                }

                @Override
                public InputPort<T> consumer(int i) {
                    if (!initialized)
                        throw new IllegalStateException();
                    InputPort<T> consumer = _innerSetup.consumer(i);
                    MappingFunction<T> func = mapper.apply(i);
                    return t -> {
                        if (t == null) {
                            consumer.put(null);
                            return;
                        }
                        T r = func.apply(t);
                        if (r != null)
                            consumer.put(r);
                    };
                }
            };
        }
        return null;
    }

    @Override
    public MappingFunction<T> getMapper(int iDataset) {
        Function<Integer, MappingFunction<T>> mapper = mapperRef.get();
        if (mapper == null)
            return MappingFunction.identity();
        return mapper.apply(iDataset);
    }

    public static final class Factory<T> implements SetPreprocessorFactory<T> {
        @JsonProperty("chain")
        public final List<SetPreprocessorFactory<T>> chain;

        @JsonCreator
        public Factory(@JsonProperty("chain") List<SetPreprocessorFactory<T>> chain) {
            this.chain = chain;
        }

        public Factory(SetPreprocessorFactory<T>... chain) {
            this(Arrays.asList(chain));
        }

        @Override
        public String[] description() {
            return chain.stream()
                    .map(SetPreprocessorFactory::description)
                    .filter(Objects::nonNull)
                    .flatMap(Arrays::stream)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }

        @Override
        public SetPreprocessor<T> getInstance() {
            return new PreprocessorChain<>(chain.stream()
                    .map(SetPreprocessorFactory::getInstance)
                    .collect(Collectors.toList()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Factory<?> that = (Factory<?>) o;
            return Objects.equals(chain, that.chain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chain);
        }
    }
}
