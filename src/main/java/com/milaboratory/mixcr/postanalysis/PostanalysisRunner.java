package com.milaboratory.mixcr.postanalysis;

import com.milaboratory.util.CanReportProgressAndStage;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class PostanalysisRunner<T> implements CanReportProgressAndStage {
    private final List<Characteristic<?, T>> characteristics = new ArrayList<>();
    private Iterable<T>[] datasets;

    public void addCharacteristics(Characteristic<?, T>... chs) {
        addCharacteristics(Arrays.asList(chs));
    }

    public void addCharacteristics(List<Characteristic<?, T>> chs) {
        characteristics.addAll(chs);
    }

    public void setDatasets(Iterable<T>[] sets) {
        this.datasets = sets;
    }

    public void setDatasets(List<Iterable<T>> sets) {
        setDatasets(sets.toArray(new Iterable[0]));
    }

    private Map<Characteristic<?, T>, MetricValue<?>[][]> result;

    private volatile String stage;
    private volatile double progress = 0.0;
    private volatile boolean isFinished = false;

    @Override
    public String getStage() {
        return stage;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    /** returns matrix[sample][metric_values] */
    public PostanalysisResult run() {
        stage = "Preparing";
        progress = 0.0;
        isFinished = false;

        Map<SetPreprocessor<T>, List<Characteristic<?, T>>> characteristicsByPrep =
                characteristics.stream()
                        .collect(Collectors.groupingBy(c -> c.preprocessor));

        Map<Characteristic<?, T>, MetricValue<?>[][]> result = new IdentityHashMap<>();

        for (Map.Entry<SetPreprocessor<T>, List<Characteristic<?, T>>> e : characteristicsByPrep.entrySet()) {
            Function<Iterable<T>, Iterable<T>> prepFunction = e.getKey().setup(datasets);
            for (int setIndex = 0; setIndex < datasets.length; setIndex++) {
                Iterable<T> set = datasets[setIndex];
                List<Characteristic<?, T>> characteristics = e.getValue();

                List<Aggregator<?, T>> aggregators = characteristics.stream()
                        .map(Characteristic::createAggregator)
                        .collect(Collectors.toList());

                for (T o : prepFunction.apply(set))
                    for (Aggregator<?, T> agg : aggregators)
                        agg.consume(o);

                for (int charIndex = 0; charIndex < characteristics.size(); charIndex++) {
                    @SuppressWarnings("unchecked")
                    MetricValue<?>[][] charValues = result.computeIfAbsent(
                            characteristics.get(charIndex),
                            __ -> new MetricValue[datasets.length][]);
                    charValues[setIndex] = aggregators.get(charIndex).result();
                }
            }
        }

        isFinished = true;
        this.result = result;
        return PostanalysisResult.create(result);
    }
}
