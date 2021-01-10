package com.milaboratory.mixcr.postanalysis.diversity;

import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.TestObject;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResultCell;
import com.milaboratory.mixcr.postanalysis.ui.GroupSummary;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
public class DiversityCharacteristicTest {
    @Test
    @SuppressWarnings("unchecked")
    public void test1() {
        DiversityCharacteristic<TestObject> diversity = new DiversityCharacteristic<>(
                "diversity", t -> t.weight, new NoPreprocessing<>());

        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());
        int nDatasets = 100;
        List<TestObject>[] datasets = TestObject.generateDatasets(nDatasets, rng,
                r -> r.nextInt(1000, 10000),
                r -> r.nextUniform(0, 1),
                r -> r.nextInt(10, 20));

        PostanalysisRunner<TestObject> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(diversity);
        runner.setDatasets(datasets);
        PostanalysisResult result = runner.run();
        result = result.setSampleIds(IntStream.range(0, nDatasets).mapToObj(String::valueOf).collect(Collectors.toList()));

        CharacteristicGroup<DiversityMeasure, TestObject> group = new CharacteristicGroup<>("diversity",
                Arrays.asList(diversity),
                Arrays.asList(new GroupSummary<>()));

        CharacteristicGroupResult<DiversityMeasure> table = result.getTable(group);
        for (CharacteristicGroupResultCell<DiversityMeasure> cell : table.cells) {
            List<TestObject> ds = datasets[cell.sampleIndex];
            if (cell.key == DiversityMeasure.InverseSimpson)
                Assert.assertEquals(SimpsonIndex(ds), cell.value, 1e-6);
            if (cell.key == DiversityMeasure.ShannonWeiner)
                Assert.assertEquals(ShannonEntropy(ds), cell.value, 1e-6);
        }
    }

    private static double ShannonEntropy(List<TestObject> dataset) {
        long sum = dataset.stream().mapToLong(d -> (long) d.weight).sum();

        double entropy = 0;
        for (TestObject e : dataset) {
            double p = e.weight / sum;
            entropy -= p * Math.log(p);
        }

        return entropy;
    }

    private static double SimpsonIndex(List<TestObject> dataset) {
        return entropy(dataset, 2);
    }

    private static double entropy(List<TestObject> dataset, double alpha) {
        if (alpha == 1)
            return ShannonEntropy(dataset);

        long sum = dataset.stream().mapToLong(d -> (long) d.weight).sum();

        double agg = 0;
        for (TestObject e : dataset) {
            double p = e.weight / sum;
            agg += Math.pow(p, alpha);
        }

        return Math.pow(agg, 1 / (1 - alpha));
    }
}
