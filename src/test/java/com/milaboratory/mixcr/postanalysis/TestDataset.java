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
package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.IteratorOutputPortAdapter;
import com.milaboratory.util.OutputPortWithExpectedSizeKt;
import com.milaboratory.util.OutputPortWithProgress;
import org.apache.commons.math3.random.RandomDataGenerator;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 *
 */
public class TestDataset<T> implements Dataset<T>, Iterable<T> {
    public final List<T> data;
    public final String id;

    public TestDataset(List<T> data) {
        this.data = data;
        this.id = UUID.randomUUID().toString();
    }

    public TestDataset(Dataset<T> data) {
        this.data = new ArrayList<>();
        try (OutputPort<T> port = data.mkElementsPort()) {
            for (T t : CUtils.it(port)) {
                this.data.add(t);
            }
        }
        this.id = data.id();
    }

    @Override
    public Iterator<T> iterator() {
        return data.iterator();
    }

    public Stream<T> stream() {
        return data.stream();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OutputPortWithProgress<T> mkElementsPort() {
        final IteratorOutputPortAdapter<T> adapter = new IteratorOutputPortAdapter<>(data);
        return OutputPortWithExpectedSizeKt.withExpectedSize(adapter, data.size());
    }

    public static ToIntFunction<RandomDataGenerator> DEFAULT_SIZE_GEN = r -> r.nextInt(1, 5000);
    public static ToDoubleFunction<RandomDataGenerator> DEFAULT_VALUE_GEN = r -> r.nextUniform(0, 10);
    public static ToDoubleFunction<RandomDataGenerator> DEFAULT_WT_GEN = r -> r.nextUniform(0, 1.0);

    public static TestDataset<TestObject> generateDataset(RandomDataGenerator rng, int size) {
        return generateDataset(rng, size, DEFAULT_VALUE_GEN, DEFAULT_WT_GEN);
    }

    public static TestDataset<TestObject> generateDataset(RandomDataGenerator rng, int size,
                                                          ToDoubleFunction<RandomDataGenerator> values,
                                                          ToDoubleFunction<RandomDataGenerator> weights) {
        TestObject[] r = new TestObject[size];
        for (int i = 0; i < size; i++) {
            r[i] = new TestObject(
                    values.applyAsDouble(rng),
                    weights.applyAsDouble(rng));
        }
        return new TestDataset<>(Arrays.asList(r));
    }

    public static TestDataset<TestObject>[] generateDatasets(int nDatasets, RandomDataGenerator rng) {
        return generateDatasets(nDatasets, rng, DEFAULT_SIZE_GEN, DEFAULT_VALUE_GEN, DEFAULT_WT_GEN);
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
            datasets[i] = generateDataset(rng, nElements[i], values, weights);
        }
        return datasets;
    }

}
