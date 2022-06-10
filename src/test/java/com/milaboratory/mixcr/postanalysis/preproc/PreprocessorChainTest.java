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
package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.InputPort;
import com.milaboratory.mixcr.postanalysis.*;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 *
 */
public class PreprocessorChainTest {

    @Test
    @SuppressWarnings("unchecked")
    public void test1() {
        long p1 = 17;
        long p2 = 13;
        long p3 = 11;
        long p4 = 7;

        SetPreprocessorFactory<TestObject> preproc = new PreprocessorChain.Factory<>(
                new TestPreprocFactory(p1),
                new TestPreprocFactory(p2),
                new TestPreprocFactory(p3),
                new TestPreprocFactory(p4)
        );

        RandomDataGenerator rng = new RandomDataGenerator(new Well512a(System.currentTimeMillis()));
        TestDataset<TestObject> ds = rndDataset(rng, 10000);

        TestDataset<TestObject> r = new TestDataset<>(SetPreprocessor.processDatasets(preproc.newInstance(), ds)[0]);

        List<TestObject> expected = new ArrayList<>();
        for (TestObject c : ds) {
            if (Stream.of(p1, p2, p3, p4).allMatch(mod -> ((long) c.weight) % mod != 0))
                expected.add(c);
        }

        Assert.assertEquals(expected, r.data);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSetupDuplication1() {
        long p1 = 17;
        long p2 = 13;
        long p3 = 11;
        long p4 = 7;

        SetPreprocessor<TestObject> preproc = new PreprocessorChain.Factory<>(
                new TestPreprocFactory(p1),
                new TestPreprocFactory(p2),
                new TestPreprocFactory(p3),
                new TestPreprocFactory(p4)
        ).newInstance();

        RandomDataGenerator rng = new RandomDataGenerator(new Well512a(System.currentTimeMillis()));
        TestDataset<TestObject> ds = rndDataset(rng, 10000);

        while (true) {
            SetPreprocessorSetup<TestObject> setup = preproc.nextSetupStep();
            if (setup == null)
                break;
            setup.initialize(1);
            InputPort<TestObject> consumer = setup.consumer(0);
            for (TestObject t : CUtils.it(ds.mkElementsPort())) {
                consumer.put(t);
            }
            consumer.put(null);
        }
        // excessive setup call
        SetPreprocessorSetup<TestObject> nullSetup = preproc.nextSetupStep();
        Assert.assertNull(nullSetup);

        MappingFunction<TestObject> m = preproc.getMapper(0);
        List<TestObject> r = new ArrayList<>();
        for (TestObject t : CUtils.it(ds.mkElementsPort())) {
            TestObject t1 = m.apply(t);
            if (t1 != null)
                r.add(t1);
        }

        TestDataset<TestObject> result = new TestDataset<>(r);

        List<TestObject> expected = new ArrayList<>();
        for (TestObject c : ds) {
            if (Stream.of(p1, p2, p3, p4).allMatch(mod -> ((long) c.weight) % mod != 0))
                expected.add(c);
        }

        Assert.assertEquals(expected, result.data);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testStat1() {
        long p1 = 17;
        long p2 = 13;
        long p3 = 11;
        long p4 = 7;

        SetPreprocessor<TestObject> preproc = new PreprocessorChain.Factory<>(
                new TestPreprocFactory(p1),
                new TestPreprocFactory(p2),
                new TestPreprocFactory(p3),
                new TestPreprocFactory(p4)
        ).newInstance();

        RandomDataGenerator rng = new RandomDataGenerator(new Well512a(System.currentTimeMillis()));
        TestDataset<TestObject> ds = rndDataset(rng, 10000);
        SetPreprocessor.processDatasets(preproc, ds);

        for (List<SetPreprocessorStat> stat : preproc.getStat().valueCollection()) {
            Assert.assertEquals(4, stat.size());
            for (int i = 0; i < stat.size() - 1; i++) {
                Assert.assertEquals(stat.get(i).nElementsAfter, stat.get(i + 1).nElementsBefore);
                Assert.assertEquals(stat.get(i).sumWeightAfter, stat.get(i + 1).sumWeightBefore, 1e-10);
            }
        }
    }


    public static final class TestPreprocFactory implements SetPreprocessorFactory<TestObject> {
        final long modulus;

        public TestPreprocFactory(long modulus) {
            this.modulus = modulus;
        }

        @Override
        public SetPreprocessor<TestObject> newInstance() {
            return new TestPreproc(modulus);
        }

        @Override
        public String id() {
            return "" + modulus;
        }
    }

    public static final class TestPreproc implements SetPreprocessor<TestObject> {
        final TLongArrayList l = new TLongArrayList();
        final long modulus;
        private final SetPreprocessorStat.Builder<TestObject> stats;

        public TestPreproc(long modulus) {
            this.modulus = modulus;
            this.stats = new SetPreprocessorStat.Builder<>("" + modulus, t -> t.weight);
        }

        boolean initialized = false;

        @Override
        public SetPreprocessorSetup<TestObject> nextSetupStep() {
            if (!initialized) {
                initialized = true;
                return new SetPreprocessorSetup<TestObject>() {
                    @Override
                    public void initialize(int nDatasets) {}

                    @Override
                    public InputPort<TestObject> consumer(int i) {
                        return c -> {
                            if (c == null)
                                return;
                            l.add((long) c.weight);
                        };
                    }
                };
            }
            return null;
        }

        @Override
        public MappingFunction<TestObject> getMapper(int iDataset) {
            stats.clear(iDataset);
            AtomicInteger idx = new AtomicInteger(0);
            return element -> {
                stats.before(iDataset, element);
                if (l.get(idx.getAndIncrement()) % modulus == 0)
                    return null;
                stats.after(iDataset, element);
                return element;
            };
        }

        @Override
        public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
            return stats.getStatMap();
        }

        @Override
        public String id() {
            return "" + modulus;
        }
    }

    public static TestDataset<TestObject> rndDataset(RandomDataGenerator rng, int size) {
        TestObject[] r = new TestObject[size];
        for (int i = 0; i < size; i++) {
            r[i] = new TestObject(
                    Math.round(rng.nextUniform(0, 1000)),
                    Math.round(rng.nextUniform(1, 100)));
        }
        return new TestDataset<>(Arrays.asList(r));
    }
}
