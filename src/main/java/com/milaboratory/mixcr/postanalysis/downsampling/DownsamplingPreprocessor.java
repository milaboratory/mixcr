package com.milaboratory.mixcr.postanalysis.downsampling;


import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.preproc.FilteredDataset;
import gnu.trove.list.array.TLongArrayList;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import static com.milaboratory.mixcr.postanalysis.downsampling.DownsamplingUtil.*;

/**
 *
 */
public class DownsamplingPreprocessor<T> implements SetPreprocessor<T> {
    public final ToLongFunction<T> getCount;
    public final BiFunction<T, Long, T> setCount;
    @JsonProperty("downsampleValueChooser")
    public final DownsampleValueChooser downsampleValueChooser;
    @JsonProperty("seed")
    public final long seed;

    public DownsamplingPreprocessor(ToLongFunction<T> getCount, BiFunction<T, Long, T> setCount, DownsampleValueChooser downsampleValueChooser, long seed) {
        this.getCount = getCount;
        this.setCount = setCount;
        this.downsampleValueChooser = downsampleValueChooser;
        this.seed = seed;
    }

    public String[] description() {
        String ch = downsampleValueChooser.description();
        if (ch == null || ch.isEmpty())
            return new String[0];
        return new String[]{ch};
    }

    @Override
    public Function<Dataset<T>, Dataset<T>> setup(Dataset<T>[] sets) {
        long[] totals = new long[sets.length];
        for (int i = 0; i < sets.length; i++)
            totals[i] = total(getCount, sets[i]);
        long downsampling = downsampleValueChooser.compute(totals);

        Set<Dataset<T>> empty = new HashSet<>();
        for (int i = 0; i < totals.length; ++i)
            if (totals[i] < downsampling)
                empty.add(sets[i]);

        return set -> {
            if (empty.contains(set))
                //noinspection unchecked
                return EMPTY_DATASET_SUPPLIER;

            // compute counts
            long total = 0;
            TLongArrayList countsList = new TLongArrayList();
            try (OutputPortCloseable<T> port = set.mkElementsPort()) {
                for (T t : CUtils.it(port)) {
                    long c = getCount.applyAsLong(t);
                    countsList.add(c);
                    total += c;
                }
            }

            if (total < downsampling)
                //noinspection unchecked
                return EMPTY_DATASET_SUPPLIER;

            RandomGenerator rnd = new Well19937c(seed);
            long[] countsDownsampled = downsample_mvhg(countsList.toArray(), downsampling, rnd);

            return new FilteredDataset<>(
                    new DownsampledDataset<>(set, countsDownsampled, setCount),
                    t -> getCount.applyAsLong(t) != 0);
        };
    }

    private static final class DownsampledDataset<T> implements Dataset<T> {
        final Dataset<T> inner;
        final long[] countsDownsampled;
        final BiFunction<T, Long, T> setCount;

        public DownsampledDataset(Dataset<T> inner, long[] countsDownsampled, BiFunction<T, Long, T> setCount) {
            this.inner = inner;
            this.countsDownsampled = countsDownsampled;
            this.setCount = setCount;
        }

        @Override
        public String id() {
            return inner.id();
        }

        @Override
        public OutputPortCloseable<T> mkElementsPort() {
            final OutputPortCloseable<T> inner = this.inner.mkElementsPort();
            final AtomicInteger index = new AtomicInteger(0);
            return new OutputPortCloseable<T>() {
                @Override
                public void close() {
                    inner.close();
                }

                @Override
                public T take() {
                    T t = inner.take();
                    if (t == null)
                        return null;
                    return setCount.apply(t, countsDownsampled[index.getAndIncrement()]);
                }
            };
        }
    }
}
