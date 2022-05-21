package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.InputPort;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class PreprocessorChain<T> implements SetPreprocessor<T> {
    final List<SetPreprocessor<T>> chain;
    final String id;

    public PreprocessorChain(List<SetPreprocessor<T>> chain, String id) {
        this.chain = chain.stream().flatMap(c ->
                        c instanceof PreprocessorChain
                                ? ((PreprocessorChain<T>) c).chain.stream()
                                : Stream.of(c))
                .collect(Collectors.toList());
        this.id = id;
    }

    private final AtomicReference<Function<Integer, MappingFunction<T>>> mapperRef = new AtomicReference<>(i -> t -> t);
    private int lastActive = 0;
    private boolean setupDone;

    @Override
    public SetPreprocessorSetup<T> nextSetupStep() {
        if (setupDone)
            return null;
        SetPreprocessorSetup<T> innerStep = null;
        int i = lastActive;
        for (; i < chain.size(); i++) {
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
        assert innerStep != null || i == chain.size();

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

        setupDone = true;
        return null;
    }

    @Override
    public MappingFunction<T> getMapper(int iDataset) {
        Function<Integer, MappingFunction<T>> mapper = mapperRef.get();
        if (mapper == null)
            return MappingFunction.identity();
        return mapper.apply(iDataset);
    }

    @Override
    public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
        TIntObjectHashMap<List<SetPreprocessorStat>> r = new TIntObjectHashMap<>();
        for (TIntObjectHashMap<List<SetPreprocessorStat>> stat :
                chain.stream().map(SetPreprocessor::getStat).collect(Collectors.toList())) {

            TIntObjectIterator<List<SetPreprocessorStat>> it = stat.iterator();
            while (it.hasNext()) {
                it.advance();
                List<SetPreprocessorStat> l = r.get(it.key());
                if (l == null) {
                    r.put(it.key(), l = new ArrayList<>());
                }
                l.addAll(it.value());
            }
        }

        return r;
    }

    @Override
    public String id() {
        return id;
    }

    public static final class Factory<T> implements SetPreprocessorFactory<T> {
        @JsonProperty("chain")
        public final List<SetPreprocessorFactory<T>> chain;

        @JsonCreator
        public Factory(@JsonProperty("chain") List<SetPreprocessorFactory<T>> chain) {
            this.chain = chain;
        }

        @SafeVarargs
        public Factory(SetPreprocessorFactory<T>... chain) {
            this(Arrays.asList(chain));
        }

        @Override
        public String id() {
            return chain.stream()
                    .map(SetPreprocessorFactory::id)
                    .collect(Collectors.joining(" | "));
        }

        @Override
        public SetPreprocessor<T> newInstance() {
            return new PreprocessorChain<>(chain.stream()
                    .map(SetPreprocessorFactory::newInstance)
                    .collect(Collectors.toList()), id());
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
