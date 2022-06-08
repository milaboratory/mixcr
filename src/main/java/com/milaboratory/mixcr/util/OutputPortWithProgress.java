package com.milaboratory.mixcr.util;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.util.CanReportProgress;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public interface OutputPortWithProgress<T> extends OutputPortCloseable<T>, CanReportProgress {
    /**
     * @return number of returned elemens so far
     */
    long currentIndex();

    static <T> OutputPortWithProgress<T> wrap(CanReportProgress progressReporter, OutputPortCloseable<T> inner) {
        final AtomicBoolean isFinished = new AtomicBoolean(false);
        final AtomicLong index = new AtomicLong(0);
        return new OutputPortWithProgress<T>() {
            @Override
            public long currentIndex() {
                return index.get();
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

    static <T> OutputPortWithProgress<T> wrap(long expectedSize, OutputPortCloseable<? extends T> inner) {
        final AtomicBoolean isFinished = new AtomicBoolean(false);
        final AtomicLong index = new AtomicLong(0);
        return new OutputPortWithProgress<T>() {
            @Override
            public long currentIndex() {
                return index.get();
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
}
