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
package com.milaboratory.mixcr.postanalysis.additive;

import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.TestDataset;
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
                NoPreprocessing.factory(),
                o -> o.weight,
                o -> "sum",
                w -> w.value,
                AggregationType.Sum,
                false
        );

        AdditiveCharacteristic<String, TestObject> mean = new AdditiveCharacteristic<>(
                "mean",
                NoPreprocessing.factory(),
                o -> o.weight,
                o -> "mean",
                w -> w.value,
                AggregationType.Mean,
                false
        );

        AdditiveCharacteristic<String, TestObject> std = new AdditiveCharacteristic<>(
                "std",
                NoPreprocessing.factory(),
                o -> o.weight,
                o -> "std",
                w -> w.value,
                AggregationType.Std,
                false
        );


        RandomDataGenerator rng = new RandomDataGenerator(new Well44497a());
        int nDatasets = 1000;
        TestDataset<TestObject>[] datasets = TestDataset.generateDatasets(nDatasets, rng);

        PostanalysisRunner<TestObject> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(sum, mean, std);
        PostanalysisResult result = runner.run(datasets);

        CharacteristicGroup<String, TestObject> group = new CharacteristicGroup<>("stat",
                Arrays.asList(mean, std),
                Arrays.asList(new GroupSummary.Simple<>()));

        CharacteristicGroupResult<String> table = result.getTable(group);
        OutputTable summary = table.getOutputs().get(GroupSummary.key);

        Object[][] rows = summary.rows();
        for (int i = 0; i < nDatasets; i++) {
            Double[] metrics = Arrays.stream(rows[i]).map(d -> (Double) d).toArray(Double[]::new);

            double[] vals = datasets[i].stream().mapToDouble(o -> o.value).toArray();
            double[] weights = datasets[i].stream().mapToDouble(o -> o.weight).toArray();

            double expectedSum = IntStream.range(0, vals.length).mapToDouble(m -> vals[m] * weights[m]).sum();
            double expectedMean = new Mean().evaluate(vals, weights);
            double expectedStd = Math.sqrt(new Variance(true).evaluate(vals, weights));

            for (int j = 0; j < metrics.length; j++) {
                if (summary.colIds.get(j).equals("sum"))
                    Assert.assertEquals(expectedSum, metrics[j], 1e-10);
                else if (summary.colIds.get(j).equals("mean"))
                    Assert.assertEquals(expectedMean, metrics[j], 1e-10);
                else if (summary.colIds.get(j).equals("std"))
                    Assert.assertEquals(expectedStd, metrics[j], 1e-10);
                else
                    throw new RuntimeException();
            }
        }
    }
}
