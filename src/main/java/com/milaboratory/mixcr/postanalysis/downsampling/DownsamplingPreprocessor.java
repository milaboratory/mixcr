package com.milaboratory.mixcr.postanalysis.downsampling;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.util.FilteredIterable;
import gnu.trove.list.array.TLongArrayList;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.HashSet;
import java.util.Iterator;
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

    @Override
    public Function<Iterable<T>, Iterable<T>> setup(Iterable<T>[] sets) {
        long[] totals = new long[sets.length];
        for (int i = 0; i < sets.length; i++)
            totals[i] = total(getCount, sets[i]);
        long downsampling = downsampleValueChooser.compute(totals);

        Set<Iterable<T>> empty = new HashSet<>();
        for (int i = 0; i < totals.length; ++i)
            if (totals[i] < downsampling)
                empty.add(sets[i]);

        return set -> {
            if (empty.contains(set))
                //noinspection unchecked
                return emptyIterable;

            // compute counts
            long total = 0;
            TLongArrayList countsList = new TLongArrayList();
            for (T t : set) {
                long c = getCount.applyAsLong(t);
                countsList.add(c);
                total += c;
            }

            if (total < downsampling)
                //noinspection unchecked
                return emptyIterable;

            RandomGenerator rnd = new Well19937c(seed);
            long[] countsDownsampled = downsample_mvhg(countsList.toArray(), downsampling, rnd);

            return new FilteredIterable<>(
                    () -> new Iterator<T>() {
                        final Iterator<T> inner = set.iterator();
                        final AtomicInteger index = new AtomicInteger(0);

                        @Override
                        public boolean hasNext() {
                            return inner.hasNext();
                        }

                        @Override
                        public T next() {
                            return setCount.apply(inner.next(), countsDownsampled[index.getAndIncrement()]);
                        }
                    },
                    t -> getCount.applyAsLong(t) != 0);
        };
    }
}
