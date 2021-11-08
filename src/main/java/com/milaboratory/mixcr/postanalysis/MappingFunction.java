package com.milaboratory.mixcr.postanalysis;

import java.util.function.Function;

/**
 *
 */
public interface MappingFunction<T> extends Function<T, T> {
    MappingFunction<?> identity = t -> t;

    @SuppressWarnings("unchecked")
    static <T> MappingFunction<T> identity() {
        return (MappingFunction<T>) identity;
    }
}
