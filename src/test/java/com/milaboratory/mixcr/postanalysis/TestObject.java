package com.milaboratory.mixcr.postanalysis;

import org.apache.commons.math3.random.RandomDataGenerator;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 *
 */
public class TestObject {
    public final double value;
    public final double weight;

    public TestObject(double value, double weight) {
        this.value = value;
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestObject that = (TestObject) o;
        return Double.compare(that.value, value) == 0 && Double.compare(that.weight, weight) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, weight);
    }

    public static TestDataset<TestObject>[] generateDatasets(int nDatasets, RandomDataGenerator rng) {
        return generateDatasets(nDatasets, rng,
                r -> r.nextInt(1, 1000),
                r -> r.nextUniform(0, 10),
                r -> r.nextUniform(0, 10));
    }

    public static TestDataset<TestObject>[] generateDatasets(int nDatasets, RandomDataGenerator rng,
                                                             ToIntFunction<RandomDataGenerator> sizes,
                                                             ToDoubleFunction<RandomDataGenerator> values,
                                                             ToDoubleFunction<RandomDataGenerator> weights) {
        int[] nElements = new int[nDatasets];
        for (int i = 0; i < nDatasets; i++) {
            nElements[i] = sizes.applyAsInt(rng);
        }
        @SuppressWarnings("unchecked")
        TestDataset<TestObject>[] datasets = new TestDataset[nDatasets];
        for (int i = 0; i < datasets.length; i++) {
            TestObject[] ds = new TestObject[nElements[i]];
            for (int j = 0; j < nElements[i]; j++) {
                TestObject w = new TestObject(
                        values.applyAsDouble(rng),
                        weights.applyAsDouble(rng));
                ds[j] = w;
            }
            datasets[i] = new TestDataset<>(Arrays.asList(ds));
        }
        return datasets;
    }
}
