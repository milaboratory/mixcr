package com.milaboratory.mixcr.util;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.util.CanReportProgress;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public interface OutputPortWithProgress<T> extends OutputPortCloseable<T>, CanReportProgress {
    /**
     * @return number of returned elemens so far
     */
    long index();

    static <T> OutputPortWithProgress<T> wrap(int size, OutputPortCloseable<T> inner) {
        final AtomicBoolean isFinished = new AtomicBoolean(false);
        final AtomicLong index = new AtomicLong(0);
        return new OutputPortWithProgress<T>() {
            @Override
            public long index() {
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
                return (index.get() + 1.0) / size;
            }

            @Override
            public boolean isFinished() {
                return isFinished.get();
            }

            @Override
            public void close() {
                inner.close();
            }
        };
    }
}
