package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.util.CanReportProgressAndStage;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param <T> type of objects
 */
public class PostanalysisRunner<T> implements CanReportProgressAndStage {
    private final List<Characteristic<?, T>> characteristics = new ArrayList<>();

    public void addCharacteristics(CharacteristicGroup<?, T>... groups) {
        for (CharacteristicGroup<?, T> g : groups) {
            addCharacteristics(g.characteristics);
        }
    }

    public void addCharacteristics(Characteristic<?, T>... chs) {
        addCharacteristics(Arrays.asList(chs));
    }

    public void addCharacteristics(List<? extends Characteristic<?, T>> chs) {
        characteristics.addAll(chs);
    }

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

    @SuppressWarnings("unchecked")
    public PostanalysisResult run(List<Dataset<T>> datasets) {
        return run(datasets.toArray(new Dataset[0]));
    }

    public PostanalysisResult run(Dataset<T>... datasets) {
        stage = "Preparing";
        progress = 0.0;
        isFinished = false;

        Map<SetPreprocessor<T>, List<Characteristic<?, T>>> characteristicsByPrep =
                characteristics.stream()
                        .collect(Collectors.groupingBy(c -> c.preprocessor));

        Map<Characteristic<?, T>, Map<String, MetricValue<?>[]>> result = new IdentityHashMap<>();

        for (Map.Entry<SetPreprocessor<T>, List<Characteristic<?, T>>> e : characteristicsByPrep.entrySet()) {
            Function<Dataset<T>, Dataset<T>> prepFunction = e.getKey().setup(datasets);
            for (Dataset<T> dataset : datasets) {
                List<Characteristic<?, T>> characteristics = e.getValue();

                List<Aggregator<?, T>> aggregators = characteristics.stream()
                        .map(c -> c.createAggregator(dataset))
                        .collect(Collectors.toList());

                try (OutputPortCloseable<T> port = prepFunction.apply(dataset).mkElementsPort()) {
                    for (T o : CUtils.it(port))
                        for (Aggregator<?, T> agg : aggregators)
                            agg.consume(o);
                }

                for (int charIndex = 0; charIndex < characteristics.size(); charIndex++) {
                    @SuppressWarnings("unchecked")
                    Map<String, MetricValue<?>[]> charValues = result.computeIfAbsent(
                            characteristics.get(charIndex),
                            __ -> new HashMap<>());
                    if (charValues.containsKey(dataset.id()))
                        throw new IllegalArgumentException("Dataset occurred twice.");
                    charValues.put(dataset.id(), aggregators.get(charIndex).result());
                }
            }
        }

        isFinished = true;
        Set<String> datasetIds = Arrays.stream(datasets)
                .map(Dataset::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return PostanalysisResult.create(datasetIds, result);
    }
}
