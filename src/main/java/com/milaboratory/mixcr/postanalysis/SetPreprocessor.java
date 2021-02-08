package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessor;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesOverlapDownsamplingPreprocessor;
import com.milaboratory.mixcr.postanalysis.preproc.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 *
 */
@JsonAutoDetect
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NoPreprocessing.class, name = "none"),
        @JsonSubTypes.Type(value = ClonesDownsamplingPreprocessor.class, name = "downsample"),
        @JsonSubTypes.Type(value = ClonesOverlapDownsamplingPreprocessor.class, name = "overlapDownsample"),
        @JsonSubTypes.Type(value = PreprocessorChain.class, name = "chain"),
        @JsonSubTypes.Type(value = FilterPreprocessor.class, name = "filter"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface SetPreprocessor<T> {
    Function<Iterable<T>, Iterable<T>> setup(Iterable<T>[] sets);

    default String[] description() {
        return new String[0];
    }

    default SetPreprocessor<T> filter(boolean before, ElementPredicate<T>... predicates) {
        FilterPreprocessor<T> filter = new FilterPreprocessor<>(predicates);
        if (this instanceof PreprocessorChain) {
            PreprocessorChain<T> p = (PreprocessorChain<T>) this;
            List<SetPreprocessor<T>> list = new ArrayList<>();
            if (before) {
                list.add(filter);
                list.addAll(p.chain);
            } else {
                list.addAll(p.chain);
                list.add(filter);
            }
            return new PreprocessorChain<>(list);
        } else
            return before ? new PreprocessorChain<>(filter, this) : new PreprocessorChain<>(this, filter);
    }

    default SetPreprocessor<T> filterAfter(ElementPredicate<T>... predicates) {
        return filter(false, predicates);
    }

    default SetPreprocessor<T> filterFirst(ElementPredicate<T>... predicates) {
        return filter(true, predicates);
    }
}
