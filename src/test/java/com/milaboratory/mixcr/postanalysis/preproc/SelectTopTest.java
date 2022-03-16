package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.CUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.*;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 *
 */
public class SelectTopTest {
    @SuppressWarnings("unchecked")
    @Test
    public void test1() {
        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());
        int nIterations = 10;
        for (int i = 0; i < nIterations; i++) {
            TestDataset<TestObject> dataset = rndDataset(rng, 10000);
            double total = 0;
            for (TestObject d : dataset) {
                total += d.weight;
            }

            double topFraction = 0.5 + (1.0 * i / nIterations / 10);
            int nTop = rng.nextInt(1, dataset.data.size());

            ArrayList<TestObject> list = new ArrayList<>(dataset.data);
            list.sort(Comparator.comparing(t -> -t.weight));
            double expectedSumCum = 0;
            double expectedSumFixed = 0;
            boolean sumDone = false;
            int counter = 0;
            for (TestObject o : list) {
                if (!sumDone && expectedSumCum < topFraction * total)
                    expectedSumCum += o.weight;
                else
                    sumDone = true;

                if (counter < nTop) {
                    expectedSumFixed += o.weight;
                    ++counter;
                }

                if (expectedSumCum > topFraction * total && counter > nTop)
                    break;
            }

            SelectTop<TestObject> cumulativeTopProc = new SelectTop<>(o -> o.weight, topFraction, -1, "");
            Dataset<TestObject> topCumulative = SetPreprocessorFactory.processDatasets(cumulativeTopProc, dataset)[0];

            double actualCum = 0;
            for (TestObject o : CUtils.it(topCumulative.mkElementsPort())) {
                actualCum += o.weight;
            }
            Assert.assertEquals(expectedSumCum, actualCum, 1e-5);

            SelectTop<TestObject> fixedTopProc = new SelectTop<>(o -> o.weight, Double.NaN, nTop, "");
            Dataset<TestObject> topFixed = SetPreprocessorFactory.processDatasets(fixedTopProc, dataset)[0];
            double actualFixed = 0;
            int actualNTop = 0;
            for (TestObject o : CUtils.it(topFixed.mkElementsPort())) {
                actualFixed += o.weight;
                ++actualNTop;
            }
            Assert.assertEquals(expectedSumFixed, actualFixed, 1e-5);
            Assert.assertEquals(nTop, actualNTop);
        }
    }

    @Test
    public void test2() {
        TestDataset<TestObject> dataset = new TestDataset<>(Arrays.asList(
                new TestObject(1, 1),
                new TestObject(1, 1),
                new TestObject(1, 1),
                new TestObject(1, 2),
                new TestObject(1, 2),
                new TestObject(1, 2)
        ));

        assertFraction(0.5, dataset, 6.0);
        assertFraction(0.8, dataset, 8.0);
    }

    @Test
    public void test3() {
        TestDataset<TestObject> dataset = new TestDataset<>(Arrays.asList(
                new TestObject(1, 1),
                new TestObject(1, 1),
                new TestObject(1, 1),
                new TestObject(1, 2),
                new TestObject(1, 2),
                new TestObject(1, 2),
                new TestObject(1, 3),
                new TestObject(1, 3)
        ));

        assertFraction(0.5, dataset, 8.0);
        assertFraction(0.5, dataset, 8.0);
    }

    @Test
    public void test4() {
        TestDataset<TestObject> dataset = new TestDataset<>(Arrays.asList(
                new TestObject(1, 1.1),
                new TestObject(1, 2.2),
                new TestObject(1, 3.3),
                new TestObject(1, 4.4),
                new TestObject(1, 5.5),
                new TestObject(1, 6.6),
                new TestObject(1, 7.7),
                new TestObject(1, 8.8)
        ));

        assertFraction(0.5, dataset, 23.1);
    }

    private void assertFraction(double topFraction, TestDataset<TestObject> dataset, double expectedSum) {
        ArrayList<TestObject> list = new ArrayList<>(dataset.data);
        list.sort(Comparator.comparing(t -> -t.weight));

        SelectTop<TestObject> cumulativeTopProc = new SelectTop<>(o -> o.weight, topFraction, -1, "");
        Dataset<TestObject> topCumulative = SetPreprocessorFactory.processDatasets(cumulativeTopProc, dataset)[0];

        double actualCum = 0;
        for (TestObject o : CUtils.it(topCumulative.mkElementsPort())) {
            actualCum += o.weight;
        }
        Assert.assertEquals(expectedSum, actualCum, 1e-5);
    }

    public static TestDataset<TestObject> rndDataset(RandomDataGenerator rng, int size) {
        TestObject[] r = new TestObject[size];
        for (int i = 0; i < size; i++) {
            r[i] = new TestObject(
                    rng.nextUniform(0, 1),
                    Math.round(rng.nextUniform(1, 100)));
        }
        return new TestDataset<>(Arrays.asList(r));
    }

    @Test
    public void testJson() throws JsonProcessingException {
        SelectTop.Factory<Clone> top = new SelectTop.Factory<>(WeightFunctions.Count, 0.8);
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        String str = om.writeValueAsString(top);
        Assert.assertEquals(top, om.readValue("{\n" +
                "  \"type\" : \"selectTop\",\n" +
                "  \"weight\" : {\n" +
                "    \"type\" : \"count\"\n" +
                "  },\n" +
                "  \"abundanceFraction\" : 0.8\n" +
                "}", SelectTop.Factory.class));
        Assert.assertEquals(top, om.readValue(str, SelectTop.Factory.class));
    }
}
