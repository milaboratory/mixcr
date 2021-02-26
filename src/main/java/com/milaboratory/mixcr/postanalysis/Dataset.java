package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.OutputPortCloseable;

/**
 *
 */
public interface Dataset<T> {
    /** Unique dataset identifier */
    String id();

    /** Closeable port of dataset elements */
    OutputPortCloseable<T> mkElementsPort();

    static <K> Dataset<K> empty(String id) {
        return new Dataset<K>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public OutputPortCloseable<K> mkElementsPort() {
                return new OutputPortCloseable<K>() {
                    @Override
                    public void close() {}

                    @Override
                    public K take() {
                        return null;
                    }
                };
            }
        };
    }
}
