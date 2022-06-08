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
package com.milaboratory.mixcr.postanalysis.downsampling;

import cc.redberry.pipe.CUtils;
import com.milaboratory.mixcr.postanalysis.*;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.FilterPreprocessor;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.postanalysis.downsampling.DownsamplingPreprocessorTest.toList;

/**
 *
 */
public class OverlapDownsamplingPreprocessorTest {
    @Test
    public void test1() {
        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());
        DownsampleValueChooser.Minimal chooser = new DownsampleValueChooser.Minimal();
        DatasetSupport[] inputs = new DatasetSupport[]{
                rndDataset(rng, 5, 100000),
                rndDataset(rng, 10, 10000),
                rndDataset(rng, 15, 10000)
        };
        for (DatasetSupport dataset : inputs) {
            DatasetSupport[] datasets = new DatasetSupport[]{dataset};

            DownsamplingPreprocessor<TestObject> proc = new DownsamplingPreprocessor<>(
                    chooser, t -> Math.round(t.weight),
                    (t, newW) -> new TestObject(t.value, newW),
                    true,
                    ""
            );

            Dataset<OverlapGroup<TestObject>>[] downsampled = SetPreprocessor.processDatasets(new OverlapPreprocessorAdapter<>(proc), datasets);

            for (int i = 0; i < datasets.length; i++) {
                DatasetSupport in = datasets[i];

                long dsValue = chooser.compute(in.counts);
                DatasetSupport dw = new DatasetSupport(downsampled[i]);

                for (int j = 0; j < in.counts.length; j++) {
                    Assert.assertEquals(dsValue, dw.counts[j]);
                }

                for (OverlapGroup<TestObject> row : dw) {
                    for (int j = 0; j < row.size(); j++) {
                        for (TestObject t : row.getBySample(j)) {
                            Assert.assertTrue(in.sets[j].contains(t.value));
                        }
                    }
                }
            }

            TIntObjectHashMap<List<SetPreprocessorStat>> stat = proc.getStat();
            Assert.assertEquals(dataset.sets.length, stat.size());
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
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test2() {
        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());
        int nDatasets = 50;
        DatasetSupport dataset = rndDataset(rng, nDatasets, 10000);
        List<TestDataset<TestObject>> individual = o2i(nDatasets, dataset);

        long[] totals = individual
                .stream()
                .mapToLong(it -> (long) CUtils.stream(it.mkElementsPort()).mapToDouble(c -> c.weight).sum())
                .toArray();
        Arrays.stream(totals).forEach(System.out::println);
        System.out.println(">>> " + new DownsampleValueChooser.Auto().compute(totals));


        SetPreprocessorFactory<TestObject> proc = new DownsamplingPreprocessorFactory<>(
                new DownsampleValueChooser.Auto(),
                t -> Math.round(t.weight),
                TestObject::setWeight,
                true
        );

        FilterPreprocessor.Factory<TestObject> filter = new FilterPreprocessor.Factory<>(t -> t.weight, ElementPredicate.mk("", t -> Double.toString(t.value).hashCode() % 3 == 1));
        SetPreprocessorFactory<TestObject> iProc = proc.before(filter);
        SetPreprocessorFactory<OverlapGroup<TestObject>> oProc = new OverlapPreprocessorAdapter.Factory<>(proc)
                .before(new OverlapPreprocessorAdapter.Factory<>(filter));

        Dataset<TestObject>[] expected = SetPreprocessor.processDatasets(iProc.newInstance(), individual);
        Dataset<OverlapGroup<TestObject>> overlapDownsampled = SetPreprocessor.processDatasets(oProc.newInstance(), dataset)[0];

        List<TestDataset<TestObject>> actual = o2i(nDatasets, overlapDownsampled);

        List<List<TestObject>> e = Arrays.stream(expected).map(d -> CUtils.toList(d.mkElementsPort())).collect(Collectors.toList());
        List<List<TestObject>> a = actual.stream().map(d -> CUtils.toList(d.mkElementsPort())).collect(Collectors.toList());

        Assert.assertEquals(e, a);
    }


    @SuppressWarnings("unchecked")
    private static List<TestDataset<TestObject>> o2i(int nDatasets, Dataset<OverlapGroup<TestObject>> od) {
        List<TestObject>[] ids = new List[nDatasets];
        for (int i = 0; i < nDatasets; i++)
            ids[i] = new ArrayList<>();
        for (OverlapGroup<TestObject> row : CUtils.it(od.mkElementsPort()))
            for (int i = 0; i < row.size(); i++)
                for (TestObject o : row.getBySample(i))
                    ids[i].add(o);
        return Arrays.stream(ids).map(TestDataset::new).collect(Collectors.toList());
    }

    private static long[] sum(long[] a, long[] b) {
        if (a.length == 0)
            return b;
        if (b.length == 0)
            return a;

        long[] r = new long[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i] + b[i];
        }
        return r;
    }

    private static final class DatasetSupport extends TestDataset<OverlapGroup<TestObject>> {
        final long[] counts;
        final Set<Double>[] sets;

        public DatasetSupport(Dataset<OverlapGroup<TestObject>> data) {
            this(toList(CUtils.it(data.mkElementsPort())));
        }

        public DatasetSupport(List<OverlapGroup<TestObject>> data) {
            super(data);
            this.counts = data.stream()
                    .map(row -> row.stream().mapToLong(l -> Math.round(l.stream().mapToDouble(e -> e.weight).sum())).toArray())
                    .reduce(new long[0], OverlapDownsamplingPreprocessorTest::sum);
            //noinspection unchecked
            this.sets = new Set[counts.length];
            for (OverlapGroup<TestObject> row : data) {
                for (int i = 0; i < row.size(); i++) {
                    Set<Double> s = sets[i];
                    if (s == null)
                        s = sets[i] = new HashSet<>();
                    for (TestObject t : row.getBySample(i)) {
                        s.add(t.value);
                    }
                }
            }
        }
    }

    private static DatasetSupport rndDataset(RandomDataGenerator rng, int nSamples, int size) {
        List<OverlapGroup<TestObject>> data = new ArrayList<>();
        int p = Math.max(2, nSamples / 4);
        for (int i = 0; i < size; i++) {
            List<List<TestObject>> row = new ArrayList<>(nSamples);
            boolean added = false;
            for (int j = 0; j < nSamples; j++) {
                if (rng.nextInt(0, p) == 0) {
                    added = true;
                    ArrayList<TestObject> cell = new ArrayList<>();
                    for (int k = 0; k < rng.nextInt(1, 2); k++) {
                        cell.add(
                                new TestObject(rng.nextUniform(0, 1),
                                        rng.nextInt(1, 1000)));
                    }
                    row.add(cell);
                } else {
                    row.add(new ArrayList<>());
                }
            }
            if (!added) {
                --i;
                continue;
            }
            data.add(new OverlapGroup<>(row));
        }
        return new DatasetSupport(data);
    }
}
