package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;

import java.util.*;
import java.util.function.Function;

/**
 *
 */
public class PreprocessorChain<T> implements SetPreprocessor<T> {
    @JsonProperty("chain")
    public final List<SetPreprocessor<T>> chain;

    @JsonCreator
    public PreprocessorChain(@JsonProperty("chain") List<SetPreprocessor<T>> chain) {
        this.chain = chain;
    }

    public PreprocessorChain(SetPreprocessor<T>... chain) {
        this(Arrays.asList(chain));
    }

    @Override
    public Function<Iterable<T>, Iterable<T>> setup(Iterable<T>[] sets) {
        Iterable<T>[] proc = sets;
        for (SetPreprocessor<T> p : chain) {
            Function<Iterable<T>, Iterable<T>> func = p.setup(proc);
            //noinspection unchecked
            proc = Arrays.stream(proc).map(func).toArray(Iterable[]::new);
        }
        Map<Iterable<T>, Iterable<T>> mapping = new IdentityHashMap<>();
        for (int i = 0; i < sets.length; i++)
            mapping.put(sets[i], proc[i]);
        return mapping::get;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreprocessorChain<?> that = (PreprocessorChain<?>) o;
        return Objects.equals(chain, that.chain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chain);
    }
}
