package com.milaboratory.mixcr.postanalysis.downsampling;

import cc.redberry.pipe.CUtils;
import com.milaboratory.mixcr.postanalysis.*;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 *
 */
public class DownsamplingPreprocessorTest {

    @Test
    public void test1() {
        long[] counts = {130, 10, 190, 130, 10, 190, 130, 1022, 190};
        long total = LongStream.of(counts).sum();
        Well512a rnd = new Well512a();
        RandomDataGenerator gen = new RandomDataGenerator(rnd);
        for (int i = 0; i < 100; ++i) {
            long downSampleSize = gen.nextLong(total / 10, total - 10);
            long[] sample_mvhg = DownsamplingUtil.downsample_mvhg(counts, downSampleSize, rnd);
            Assert.assertEquals(downSampleSize, Arrays.stream(sample_mvhg).sum());

            long[] sample_counts = DownsamplingUtil.downsample_counts(counts, downSampleSize, rnd);
            Assert.assertEquals(downSampleSize, Arrays.stream(sample_counts).sum());
        }
    }

    @Test
    @Ignore
    public void test2() {
        long[] arr = new long[1000];
        long total = 0;
        for (int i = 0; i < arr.length; ++i) {
            arr[i] = Math.max(1, (int) (1000 / Math.pow(1 + i, 2)));
            total += arr[i];
        }

        System.out.println(total);
        System.out.println(Arrays.toString(arr));
        Well512a rnd = new Well512a();
        RandomDataGenerator gen = new RandomDataGenerator(rnd);
        for (int i = 0; i < 100; ++i) {
            long downSampleSize = gen.nextLong(total / 10, total - 10);
            long[] sample_mvhg = DownsamplingUtil.downsample_mvhg(arr, downSampleSize, rnd);
            Assert.assertEquals(downSampleSize, Arrays.stream(sample_mvhg).sum());

            long[] sample_counts = DownsamplingUtil.downsample_counts(arr, downSampleSize, rnd);
            Assert.assertEquals(downSampleSize, Arrays.stream(sample_counts).sum());

            System.out.println(downSampleSize);
            System.out.println(Arrays.toString(sample_mvhg));
            System.out.println(Arrays.toString(sample_counts));
            System.out.println("-------");
        }
    }

    @Test
    public void testRnd() {
        DescriptiveStatistics timing = new DescriptiveStatistics();
        Well512a rnd = new Well512a();
        RandomDataGenerator gen = new RandomDataGenerator(rnd);
        int nTries = 100, nDownsamples = 3;
        for (int nTry = 0; nTry < nTries; ++nTry) {
            long[] counts = new long[gen.nextInt(1000_000, 2000_000) / 10];
            for (int i = 0; i < counts.length; ++i)
                counts[i] = (long) (gen.nextInt(50, 500) * 5000L / Math.pow(1 + i, 1) + 1);

            long sum = Arrays.stream(counts).sum();

            for (int i = 0; i < nDownsamples; ++i) {
                long downsampleSize = gen.nextLong(1, sum - 1);
                long start = System.nanoTime();
                long[] sample = DownsamplingUtil.downsample_mvhg(counts, downsampleSize, rnd);
                long elapsed = System.nanoTime() - start;

                timing.addValue(elapsed);
                Assert.assertEquals(downsampleSize, LongStream.of(sample).sum());
            }
        }
        System.out.println(timing);
    }

    @Test
    public void test3() {
        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());

        DownsampleValueChooser dsChooser = counts -> Arrays.stream(counts).min().orElse(0);

        DownsamplingPreprocessor<TestObject> proc = new DownsamplingPreprocessor<>(
                t -> Math.round(t.weight),
                (t, w) -> new TestObject(t.value, 1d * w),
                dsChooser,
                rng.nextLong(0, Long.MAX_VALUE / 2),
                ""
        );

        int nDatasets = 10;
        DatasetSupport[] initial = new DatasetSupport[nDatasets];
        for (int i = 0; i < initial.length; i++) {
            initial[i] = rndDataset(rng, rng.nextInt(100, 1000));
        }
        long dsValue = dsChooser.compute(Arrays.stream(initial).mapToLong(d -> d.count).toArray());

        DatasetSupport[] downsampled = Arrays.stream(SetPreprocessorFactory.processDatasets(proc, initial))
                .map(DatasetSupport::new)
                .toArray(DatasetSupport[]::new);

        for (int i = 0; i < downsampled.length; i++) {
            DatasetSupport in = initial[i];
            DatasetSupport dw = downsampled[i];

            Assert.assertTrue(in.set.containsAll(dw.set));
            Assert.assertEquals(dsValue, dw.count);
        }

        TIntObjectHashMap<List<SetPreprocessorStat>> stat = proc.getStat();
        Assert.assertEquals(nDatasets, stat.size());
        TIntObjectIterator<List<SetPreprocessorStat>> it = stat.iterator();
        double wtAfter = -1;
        while (it.hasNext()) {
            it.advance();
            Assert.assertEquals(1, it.value().size());
            SetPreprocessorStat istat = it.value().get(0);
            if (wtAfter == -1)
                wtAfter = istat.sumWeightAfter;
            else
                Assert.assertEquals(wtAfter, istat.sumWeightAfter, 1e-5);
        }
    }

    static <T> List<T> toList(Iterable<T> it) {
        if (it instanceof Collection)
            return new ArrayList<>((Collection<? extends T>) it);

        ArrayList<T> l = new ArrayList<>();
        for (T t : it) {
            l.add(t);
        }
        return l;
    }

    public static DatasetSupport rndDataset(RandomDataGenerator rng, int size) {
        TestObject[] r = new TestObject[size];
        for (int i = 0; i < size; i++) {
            r[i] = new TestObject(
                    rng.nextUniform(0, 1),
                    rng.nextUniform(0, 100));
        }
        return new DatasetSupport(Arrays.asList(r));
    }

    private static final class DatasetSupport extends TestDataset<TestObject> {
        final Set<Double> set;
        final long count;

        public DatasetSupport(Dataset<TestObject> data) {
            this(toList(CUtils.it(data.mkElementsPort())));
        }

        public DatasetSupport(List<TestObject> data) {
            super(data);
            this.set = data.stream().map(s -> s.value).collect(Collectors.toSet());
            this.count = data.stream().mapToLong(l -> Math.round(l.weight)).sum();
        }
    }
}
