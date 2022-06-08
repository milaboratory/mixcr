package com.milaboratory.mixcr.postanalysis;

import com.milaboratory.mixcr.postanalysis.diversity.DiversityCharacteristic;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsampleValueChooser;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.FilterPreprocessor;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PostanalysisRunnerTest {
    @Test
    public void test1() {
        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());
        int nDatasets = 50;
        TestDataset<TestObject>[] datasets = TestDataset.generateDatasets(nDatasets, rng,
                r -> r.nextInt(10, 1000),
                r -> r.nextUniform(10, 1000),
                r -> r.nextUniform(300, 1000));

        int downsampling = 500;

        FilterPreprocessor.Factory<TestObject> filter = new FilterPreprocessor.Factory<>(c -> c.weight,
                new ElementPredicate<TestObject>() {
                    @Override
                    public String id() {
                        return "filter";
                    }

                    @Override
                    public boolean test(TestObject testObject) {
                        return ((long) Math.round(testObject.value)) % 3 == 1;
                    }
                });

        DownsamplingPreprocessorFactory<TestObject> downsamplingPreproc = new DownsamplingPreprocessorFactory<>(
                new DownsampleValueChooser.Fixed(downsampling),
                c -> (long) c.weight, TestObject::setWeight, true
        );
        SetPreprocessorFactory<TestObject> preproc = filter.then(downsamplingPreproc);

        DiversityCharacteristic<TestObject> diversityCh =
                new DiversityCharacteristic<>("diversity", t -> t.weight, preproc);

        SetPreprocessorFactory<TestObject> downsamplingPreproc2 = new SelectTop.Factory<>(t -> t.weight, 0.8);
        SetPreprocessorFactory<TestObject> preproc2 = downsamplingPreproc2.then(new SelectTop.Factory<>(t -> t.weight, 0.8));
        DiversityCharacteristic<TestObject> diversityCh2 = new DiversityCharacteristic<>("d50", t -> t.weight, preproc2);

        PostanalysisRunner<TestObject> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(diversityCh, diversityCh2);
        PostanalysisResult result = runner.run(datasets);

        // two preprocessors
        assertEquals(2, result.preprocSummary.size());

        double[] wts = result.preprocSummary.get(result.data.get(diversityCh.name).preproc)
                .result.values().stream()
                .map(SetPreprocessorStat::cumulative)
                .mapToDouble(t -> t.sumWeightAfter)
                .filter(t -> t != 0) // filter dropped
                .distinct()
                .toArray();


        // assert that all diversity metrics have same sum wt
        assertEquals(1, wts.length);
        assertEquals(downsampling, wts[0], 0e-5);

        double[] div2wts = result.preprocSummary.get(result.data.get(diversityCh2.name).preproc)
                .result.values().stream()
                .map(SetPreprocessorStat::cumulative)
                .mapToDouble(t -> t.sumWeightAfter)
                .filter(t -> t != 0) // filter dropped
                .distinct()
                .toArray();


        // assert that all d50 metrics have different sum wt
        assertTrue(div2wts.length > nDatasets / 2);
    }
}