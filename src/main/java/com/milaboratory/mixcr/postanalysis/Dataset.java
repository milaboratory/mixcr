package com.milaboratory.mixcr.postanalysis;

import com.milaboratory.mixcr.util.OutputPortWithProgress;

/**
 *
 */
public interface Dataset<T> {
    /**
     * Unique dataset identifier
     */
    String id();

    /**
     * Closeable port of dataset elements
     */
    OutputPortWithProgress<T> mkElementsPort();

    static <K> Dataset<K> empty(String id) {
        return new Dataset<K>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public OutputPortWithProgress<K> mkElementsPort() {
                return new OutputPortWithProgress<K>() {
                    @Override
                    public long currentIndex() {
                        return 0;
                    }

                    @Override
                    public double getProgress() {
                        return 0.0;
                    }

                    @Override
                    public boolean isFinished() {
                        return true;
                    }

                    @Override
                    public void close() {
                    }

                    @Override
                    public K take() {
                        return null;
                    }
                };
            }
        };
    }
}
