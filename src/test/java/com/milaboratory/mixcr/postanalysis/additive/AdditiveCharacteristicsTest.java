package com.milaboratory.mixcr.postanalysis.additive;

import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.TestObject;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.GroupSummary;
import com.milaboratory.mixcr.postanalysis.ui.OutputTable;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well44497a;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
public class AdditiveCharacteristicsTest {
    @Test
    @SuppressWarnings("unchecked")
    public void testWeightedDescriptiveStat() {
        AdditiveCharacteristic<String, TestObject> sum = new AdditiveCharacteristic<>(
                "sum",
                new NoPreprocessing<>(),
                o -> o.weight,
                o -> "sum",
                w -> w.value,
                AggregationType.Sum,
                false
        );

        AdditiveCharacteristic<String, TestObject> mean = new AdditiveCharacteristic<>(
                "mean",
                new NoPreprocessing<>(),
                o -> o.weight,
                o -> "mean",
                w -> w.value,
                AggregationType.Mean,
                false
        );

        AdditiveCharacteristic<String, TestObject> std = new AdditiveCharacteristic<>(
                "std",
                new NoPreprocessing<>(),
                o -> o.weight,
                o -> "std",
                w -> w.value,
                AggregationType.Std,
                false
        );


        RandomDataGenerator rng = new RandomDataGenerator(new Well44497a());
        int nDatasets = 1000;
        List<TestObject>[] datasets = TestObject.generateDatasets(nDatasets, rng);

        PostanalysisRunner<TestObject> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(sum, mean, std);
        runner.setDatasets(datasets);
        PostanalysisResult result = runner.run();

        CharacteristicGroup<String, TestObject> group = new CharacteristicGroup<>("stat",
                Arrays.asList(mean, std),
                Arrays.asList(new GroupSummary<>()));
        result = result.setSampleIds(IntStream.range(0, nDatasets).mapToObj(String::valueOf).collect(Collectors.toList()));

        CharacteristicGroupResult<String> table = result.getTable(group);
        OutputTable summary = table.getOutputs().get(GroupSummary.key);

        Double[][] rows = summary.rows();
        for (int i = 0; i < nDatasets; i++) {
            Double[] metrics = rows[i];

            double[] vals = datasets[i].stream().mapToDouble(o -> o.value).toArray();
            double[] weights = datasets[i].stream().mapToDouble(o -> o.weight).toArray();

            double expectedSum = IntStream.range(0, vals.length).mapToDouble(m -> vals[m] * weights[m]).sum();
            double expectedMean = new Mean().evaluate(vals, weights);
            double expectedStd = Math.sqrt(new Variance(true).evaluate(vals, weights));

            for (int j = 0; j < metrics.length; j++) {
                if (summary.colNames.get(j).equals("sum"))
                    Assert.assertEquals(expectedSum, metrics[j], 1e-10);
                else if (summary.colNames.get(j).equals("mean"))
                    Assert.assertEquals(expectedMean, metrics[j], 1e-10);
                else if (summary.colNames.get(j).equals("std"))
                    Assert.assertEquals(expectedStd, metrics[j], 1e-10);
                else
                    throw new RuntimeException();
            }
        }
    }
}
