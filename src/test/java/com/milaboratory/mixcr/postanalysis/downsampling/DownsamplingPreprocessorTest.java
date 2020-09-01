package com.milaboratory.mixcr.postanalysis.downsampling;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
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
                counts[i] = (long) (gen.nextInt(50, 500) * 5000 / Math.pow(1 + i, 1) + 1);

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
}
