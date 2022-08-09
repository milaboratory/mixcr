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
                    public void finish() {

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
