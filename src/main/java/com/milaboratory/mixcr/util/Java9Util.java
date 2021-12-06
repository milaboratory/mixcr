package com.milaboratory.mixcr.util;

import java.util.Optional;
import java.util.stream.Stream;

public final class Java9Util {
    private Java9Util() {
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T> Stream<T> stream(Optional<T> optional) {
        return optional.map(Stream::of).orElseGet(Stream::empty);
    }
}
