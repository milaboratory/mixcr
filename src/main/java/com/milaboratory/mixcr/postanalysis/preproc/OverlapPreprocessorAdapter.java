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

import cc.redberry.pipe.InputPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public class OverlapPreprocessorAdapter<T> implements SetPreprocessor<OverlapGroup<T>> {
    final SetPreprocessor<T> inner;

    public OverlapPreprocessorAdapter(SetPreprocessor<T> inner) {
        this.inner = inner;
    }

    @Override
    public SetPreprocessorSetup<OverlapGroup<T>> nextSetupStep() {
        SetPreprocessorSetup<T> innerStep = inner.nextSetupStep();
        if (innerStep == null)
            return null;
        return new SetPreprocessorSetup<OverlapGroup<T>>() {
            boolean initialized = false;

            @Override
            public void initialize(int nDatasets) {
                if (initialized)
                    throw new IllegalStateException();
                initialized = true;
                if (nDatasets != 1)
                    throw new IllegalArgumentException("only one overlap allowed");
            }

            @Override
            public InputPort<OverlapGroup<T>> consumer(int iOverlap) {
                if (!initialized)
                    throw new IllegalStateException();
                if (iOverlap != 0)
                    throw new IllegalArgumentException("only one overlap allowed");
                List<InputPort<T>> innerPorts = new ArrayList<>();
                return object -> {
                    if (object == null)
                        return;

                    int overlapSize = object.size();
                    if (innerPorts.isEmpty()) {
                        innerStep.initialize(overlapSize);
                        for (int iDataset = 0; iDataset < overlapSize; iDataset++) {
                            innerPorts.add(innerStep.consumer(iDataset));
                        }
                    }

                    for (int iDataset = 0; iDataset < overlapSize; iDataset++) {
                        for (T t : object.getBySample(iDataset)) {
                            innerPorts.get(iDataset).put(t);
                        }
                    }
                };
            }
        };
    }

    @Override
    public MappingFunction<OverlapGroup<T>> getMapper(int iOverlap) {
        if (iOverlap != 0)
            throw new IllegalArgumentException("only one overlap allowed");

        List<MappingFunction<T>> innerMappers = new ArrayList<>();
        return group -> {
            int overlapSize = group.size();
            if (innerMappers.isEmpty())
                for (int iDataset = 0; iDataset < overlapSize; iDataset++) {
                    innerMappers.add(inner.getMapper(iDataset));
                }

            List<List<T>> result = new ArrayList<>();
            for (int iDataset = 0; iDataset < overlapSize; iDataset++) {
                List<T> gr = new ArrayList<>();
                for (T t : group.getBySample(iDataset)) {
                    T r = innerMappers.get(iDataset).apply(t);
                    if (r != null)
                        gr.add(r);
                }
                result.add(gr);
            }

            if (result.stream().allMatch(List::isEmpty))
                return null;
            return new OverlapGroup<>(result);
        };
    }

    @Override
    public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
        return inner.getStat();
    }

    @Override
    public String id() {
        return inner.id();
    }

    public static final class Factory<T> implements SetPreprocessorFactory<OverlapGroup<T>> {
        @JsonProperty("inner")
        public final SetPreprocessorFactory<T> inner;

        public Factory(@JsonProperty("inner") SetPreprocessorFactory<T> inner) {
            this.inner = inner;
        }

        @Override
        public String id() {
            return inner.id();
        }

        @Override
        public SetPreprocessor<OverlapGroup<T>> newInstance() {
            return new OverlapPreprocessorAdapter<>(inner.newInstance());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Factory<?> that = (Factory<?>) o;
            return Objects.equals(inner, that.inner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inner);
        }
    }
}
