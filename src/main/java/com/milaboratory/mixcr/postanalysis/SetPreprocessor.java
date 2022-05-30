package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.InputPort;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
public interface SetPreprocessor<T> {
    SetPreprocessorSetup<T> nextSetupStep();

    MappingFunction<T> getMapper(int iDataset);

    /**
     * Returns statistics per dataset or null if the dataset was excluded as result of preprocessing
     */
    TIntObjectHashMap<List<SetPreprocessorStat>> getStat();

    /** Id from {@link SetPreprocessorFactory#id()} */
    String id();

    @SafeVarargs
    static <T> Dataset<T>[] processDatasets(SetPreprocessor<T> proc, Dataset<T>... initial) {
        return processDatasets(proc, Arrays.asList(initial));
    }

    @SuppressWarnings("unchecked")
    static <T> Dataset<T>[] processDatasets(SetPreprocessor<T> proc, List<? extends Dataset<T>> initial) {
        while (true) {
            SetPreprocessorSetup<T> setup = proc.nextSetupStep();
            if (setup == null) {
                Dataset<T>[] result = new Dataset[initial.size()];
                for (int i = 0; i < initial.size(); i++) {
                    int datasetIdx = i;
                    MappingFunction<T> mapper = proc.getMapper(datasetIdx);
                    result[i] = new Dataset<T>() {
                        @Override
                        public String id() {
                            return initial.get(datasetIdx).id();
                        }

                        @Override
                        public OutputPortWithProgress<T> mkElementsPort() {
                            OutputPortWithProgress<T> inner = initial.get(datasetIdx).mkElementsPort();
                            return new OutputPortWithProgress<T>() {
                                @Override
                                public double getProgress() {
                                    return inner.getProgress();
                                }

                                @Override
                                public boolean isFinished() {
                                    return inner.isFinished();
                                }

                                @Override
                                public long index() {
                                    return inner.index();
                                }

                                @Override
                                public void close() {
                                    inner.close();
                                }

                                @Override
                                public T take() {
                                    while (true) {
                                        T t = inner.take();
                                        if (t == null)
                                            return null;

                                        T r = mapper.apply(t);
                                        if (r == null)
                                            continue;
                                        return r;
                                    }
                                }
                            };
                        }
                    };
                }
                return result;
            }
            setup.initialize(initial.size());

            List<InputPort<T>> consumers = IntStream.range(0, initial.size())
                    .mapToObj(setup::consumer)
                    .collect(Collectors.toList());

            for (int i = 0; i < initial.size(); i++) {
                try (OutputPortCloseable<T> port = initial.get(i).mkElementsPort()) {
                    for (T t : CUtils.it(port)) {
                        consumers.get(i).put(t);
                    }
                }
            }
            for (InputPort<T> c : consumers) {
                c.put(null);
            }
        }
    }
}
