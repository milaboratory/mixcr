/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.postanalysis.downsampling;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.preproc.FilteredDataset;
import gnu.trove.list.array.TLongArrayList;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

public class OverlapDownsamplingPreprocessor<T> implements SetPreprocessor<OverlapGroup<T>> {
    public final ToLongFunction<T> getCount;
    public final BiFunction<T, Long, T> setCount;
    @JsonProperty("downsampleValueChooser")
    public final DownsampleValueChooser downsampleValueChooser;
    @JsonProperty("seed")
    public final long seed;

    public OverlapDownsamplingPreprocessor(ToLongFunction<T> getCount, BiFunction<T, Long, T> setCount, DownsampleValueChooser downsampleValueChooser, long seed) {
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
    public Function<Dataset<OverlapGroup<T>>, Dataset<OverlapGroup<T>>> setup(Dataset<OverlapGroup<T>>[] sets) {
        return set -> {
            TLongArrayList[] counts = null;
            try (OutputPortCloseable<OverlapGroup<T>> port = set.mkElementsPort()) {
                for (OverlapGroup<T> grp : CUtils.it(port)) {
                    if (counts == null) {
                        counts = new TLongArrayList[grp.size()];
                        for (int i = 0; i < grp.size(); i++)
                            counts[i] = new TLongArrayList();
                    }

                    assert counts.length == grp.size();

                    for (int i = 0; i < counts.length; i++)
                        for (T t : grp.getBySample(i))
                            counts[i].add(getCount.applyAsLong(t));
                }
            }
            if (counts == null)
                // empty set
                return set;

            long[] totals = Arrays.stream(counts).mapToLong(TLongArrayList::sum).toArray();
            long downsample = downsampleValueChooser.compute(totals);

            RandomGenerator rnd = new Well19937c(seed);
            long[][] newCounts = new long[totals.length][];
            for (int i = 0; i < totals.length; i++) {
                if (totals[i] < downsample)
                    continue;
                newCounts[i] = DownsamplingUtil.downsample_mvhg(counts[i].toArray(), downsample, rnd);
            }

            return new FilteredDataset<>(
                    new DownsampledDataset<>(set, newCounts, getCount, setCount),
                    OverlapGroup::notEmpty);
        };
    }

    private static final class DownsampledDataset<T> implements Dataset<OverlapGroup<T>> {
        final Dataset<OverlapGroup<T>> inner;
        final long[][] countsDownsampled;
        final ToLongFunction<T> getCount;
        final BiFunction<T, Long, T> setCount;

        public DownsampledDataset(Dataset<OverlapGroup<T>> inner, long[][] countsDownsampled, ToLongFunction<T> getCount, BiFunction<T, Long, T> setCount) {
            this.inner = inner;
            this.countsDownsampled = countsDownsampled;
            this.getCount = getCount;
            this.setCount = setCount;
        }

        @Override
        public String id() {
            return inner.id();
        }

        @Override
        public OutputPortCloseable<OverlapGroup<T>> mkElementsPort() {
            final OutputPortCloseable<OverlapGroup<T>> inner = this.inner.mkElementsPort();
            final AtomicIntegerArray indices = new AtomicIntegerArray(countsDownsampled.length);
            return new OutputPortCloseable<OverlapGroup<T>>() {
                @Override
                public void close() {
                    inner.close();
                }

                @Override
                public OverlapGroup<T> take() {
                    OverlapGroup<T> grp = inner.take();
                    if (grp == null)
                        return null;

                    List<List<T>> newGroups = new ArrayList<>();
                    for (int i = 0; i < grp.size(); i++) {
                        final int fi = i;
                        List<T> objs = grp.getBySample(i);
                        if (objs.isEmpty())
                            newGroups.add(objs);
                        else
                            newGroups.add(objs.stream()
                                    .map(o -> {
                                        long newCount = countsDownsampled[fi] == null ? 0 : countsDownsampled[fi][indices.getAndIncrement(fi)];
                                        if (getCount.applyAsLong(o) < newCount)
                                            throw new RuntimeException("Assertion exception. Varying ordering of objects in iterator.");
                                        return setCount.apply(o, newCount);
                                    })
                                    .filter(l -> getCount.applyAsLong(l) > 0)
                                    .collect(Collectors.toList()));
                    }
                    return new OverlapGroup<>(newGroups);
                }
            };
        }
    }
}
