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
package com.milaboratory.mixcr.util;

import cc.redberry.pipe.OutputPort;
import com.milaboratory.util.CanReportProgress;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

public interface OutputPortWithProgress<T> extends OutputPort<T>, CanReportProgress {
    /**
     * @return number of returned elemens so far
     */
    long currentIndex();

    /**
     * Finish progress without closing underling port
     */
    void finish();

    static <T> OutputPortWithProgress<T> wrap(CanReportProgress progressReporter, OutputPort<T> inner) {
        final AtomicBoolean isFinished = new AtomicBoolean(false);
        final AtomicLong index = new AtomicLong(0);
        return new OutputPortWithProgress<T>() {
            @Override
            public long currentIndex() {
                return index.get();
            }

            @Override
            public void finish() {
                isFinished.set(true);
            }

            @Override
            public T take() {
                T t = inner.take();
                if (t == null) {
                    isFinished.set(true);
                    return null;
                }
                index.incrementAndGet();
                return t;
            }

            @Override
            public double getProgress() {
                return progressReporter.getProgress();
            }

            @Override
            public boolean isFinished() {
                return isFinished.get() || progressReporter.isFinished();
            }

            @Override
            public void close() {
                isFinished.set(true);
                inner.close();
            }
        };
    }

    static <T> OutputPortWithProgress<T> wrap(long expectedSize, OutputPort<? extends T> inner) {
        final AtomicBoolean isFinished = new AtomicBoolean(false);
        final AtomicLong index = new AtomicLong(0);
        return new OutputPortWithProgress<T>() {
            @Override
            public long currentIndex() {
                return index.get();
            }

            @Override
            public void finish() {
                isFinished.set(true);
            }

            @Override
            public T take() {
                T t = inner.take();
                if (t == null) {
                    isFinished.set(true);
                    return null;
                }
                index.incrementAndGet();
                return t;
            }

            @Override
            public double getProgress() {
                return 1.0 * index.get() / expectedSize;
            }

            @Override
            public boolean isFinished() {
                return isFinished.get();
            }

            @Override
            public void close() {
                isFinished.set(true);
                inner.close();
            }
        };
    }

    static <T> OutputPortWithProgress<T> wrap(long expectedSum, OutputPort<? extends T> inner, ToLongFunction<T> countPerElement) {
        final AtomicBoolean isFinished = new AtomicBoolean(false);
        final AtomicLong index = new AtomicLong(0);
        return new OutputPortWithProgress<T>() {
            @Override
            public long currentIndex() {
                return index.get();
            }

            @Override
            public void finish() {
                isFinished.set(true);
            }

            @Override
            public T take() {
                T t = inner.take();
                if (t == null) {
                    isFinished.set(true);
                    return null;
                }
                index.addAndGet(countPerElement.applyAsLong(t));
                return t;
            }

            @Override
            public double getProgress() {
                return 1.0 * index.get() / expectedSum;
            }

            @Override
            public boolean isFinished() {
                return isFinished.get();
            }

            @Override
            public void close() {
                isFinished.set(true);
                inner.close();
            }
        };
    }
}
