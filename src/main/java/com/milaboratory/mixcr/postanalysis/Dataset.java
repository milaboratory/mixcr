package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.OutputPortCloseable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 *
 */
public interface Dataset<T> {
    /** Unique dataset identifier */
    String id();

    /** Closeable port of dataset elements */
    OutputPortCloseable<T> mkElementsPort();

    /** Uses random UUID as dataset id */
    static <K> Dataset<K> fromSupplier(Supplier<OutputPortCloseable<K>> supp) {
        final String id = UUID.randomUUID().toString();
        return new Dataset<K>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public OutputPortCloseable<K> mkElementsPort() {
                return supp.get();
            }
        };
    }
}
