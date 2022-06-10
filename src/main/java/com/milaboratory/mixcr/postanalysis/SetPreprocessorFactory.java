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
package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.preproc.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    SetPreprocessor<T> newInstance();

    /** Descriptive unique identifier */
    String id();

    @SuppressWarnings("unchecked")
    default SetPreprocessorFactory<T> filter(boolean before, ElementPredicate<T>... predicates) {
        FilterPreprocessor.Factory<T> filter = new FilterPreprocessor.Factory<>(Arrays.asList(predicates), WeightFunctions.Default());
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
            return before ? before(filter) : then(filter);
    }

    default SetPreprocessorFactory<T> then(SetPreprocessorFactory<T> then) {
        return new PreprocessorChain.Factory<T>(this, then);
    }

    default SetPreprocessorFactory<T> before(SetPreprocessorFactory<T> before) {
        return new PreprocessorChain.Factory<T>(before, this);
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

}
