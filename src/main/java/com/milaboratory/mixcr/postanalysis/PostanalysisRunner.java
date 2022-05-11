package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.InputPort;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapDataset;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.CanReportProgressAndStage;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @param <T> type of objects
 */
public class PostanalysisRunner<T> implements CanReportProgressAndStage {
    private final List<Characteristic<?, T>> characteristics = new ArrayList<>();

    @SafeVarargs
    public final void addCharacteristics(CharacteristicGroup<?, T>... groups) {
        for (CharacteristicGroup<?, T> g : groups) {
            addCharacteristics(g.characteristics);
        }
    }

    @SafeVarargs
    public final void addCharacteristics(Characteristic<?, T>... chs) {
        addCharacteristics(Arrays.asList(chs));
    }

    public void addCharacteristics(List<? extends Characteristic<?, T>> chs) {
        characteristics.addAll(chs);
    }

    private volatile String stage = "Initializing";
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

    /**
     * Run PA for a given datasets
     */
    @SuppressWarnings("unchecked")
    public PostanalysisResult run(Dataset<T>... datasets) {
        stage = "Preprocessing datasets";
        progress = Double.NaN;
        isFinished = false;

        // save char indices in array for faster access
        Map<Characteristic<?, T>, Integer> char2idx = new HashMap<>();
        for (int i = 0; i < characteristics.size(); i++)
            char2idx.put(characteristics.get(i), i);
        // charID -> proc (deduplicated)
        Map<Characteristic<?, T>, SetPreprocessor<T>> char2proc = new HashMap<>();
        Map<SetPreprocessorFactory<T>, SetPreprocessor<T>> distinctProcs = new HashMap<>();
        IdentityHashMap<SetPreprocessor<T>, TIntArrayList> proc2char = new IdentityHashMap<>();
        for (Characteristic<?, T> ch : characteristics) {
            SetPreprocessor<T> proc = distinctProcs.computeIfAbsent(ch.preprocessor, SetPreprocessorFactory::newInstance);
            if (char2proc.containsKey(ch))
                throw new IllegalArgumentException("Characteristics with the same name illegal");
            char2proc.put(ch, proc);
            proc2char.computeIfAbsent(proc, __ -> new TIntArrayList()).add(char2idx.get(ch));
        }
        // distinct processors
        List<SetPreprocessor<T>> procs = new ArrayList<>(distinctProcs.values());

        while (true) {
            // next setup iterations
            Collection<SetPreprocessorSetup<T>> setupSteps = procs.stream()
                    .map(SetPreprocessor::nextSetupStep)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (setupSteps.isEmpty())
                break;

            // initialize setups
            for (SetPreprocessorSetup<T> s : setupSteps) {
                s.initialize(datasets.length);
            }
            // inputConsumers[datasetIndex] - all consumers for i-th dataset
            List<Collection<InputPort<T>>> inputConsumers = IntStream
                    .range(0, datasets.length)
                    .mapToObj(iDataset -> setupSteps.stream()
                            .map(setup -> setup.consumer(iDataset))
                            .collect(Collectors.toList())
                    ).collect(Collectors.toList());

            for (int i = 0; i < datasets.length; i++) {
                Dataset<T> ds = datasets[i];
                try (OutputPortCloseable<T> port = ds.mkElementsPort()) {
                    for (T t : CUtils.it(port)) {
                        for (InputPort<T> c : inputConsumers.get(i)) {
                            c.put(t);
                        }
                    }
                }
                for (InputPort<T> c : inputConsumers.get(i)) {
                    c.put(null);
                }
            }
        }

        // mapper functions
        IdentityHashMap<SetPreprocessor<T>, MappingFunction<T>>[] mappingFunctions = new IdentityHashMap[datasets.length];
        for (int i = 0; i < datasets.length; i++) {
            mappingFunctions[i] = new IdentityHashMap<>();
            for (SetPreprocessor<T> p : procs) {
                mappingFunctions[i].put(p, p.getMapper(i));
            }
        }

        Map<Characteristic<?, T>, Map<String, MetricValue<?>[]>> result = new HashMap<>();
        for (int i = 0; i < datasets.length; i++) {
            Dataset<T> dataset = datasets[i];
            if (datasets.length == 1)
                stage = "Processing";
            else
                stage = "Processing: " + dataset.id();

            Aggregator<?, T>[] aggregators = characteristics.stream()
                    .map(c -> c.createAggregator(dataset))
                    .toArray(Aggregator[]::new);

            try (OutputPortWithProgress<T> port = dataset.mkElementsPort()) {
                for (T o : CUtils.it(port)) {
                    progress = port.getProgress();
                    for (Map.Entry<SetPreprocessor<T>, MappingFunction<T>> e : mappingFunctions[i].entrySet()) {
                        MappingFunction<T> mapper = e.getValue();
                        T oMapped = mapper.apply(o);
                        if (oMapped != null)
                            for (int chIdx : proc2char.get(e.getKey()).toArray())
                                aggregators[chIdx].consume(oMapped);
                    }
                }
            }

            for (Characteristic<?, T> characteristic : characteristics) {
                Map<String, MetricValue<?>[]> charValues = result.computeIfAbsent(
                        characteristic,
                        __ -> new HashMap<>());
                if (charValues.containsKey(dataset.id()))
                    throw new IllegalArgumentException("Dataset occurred twice.");
                charValues.put(dataset.id(), aggregators[char2idx.get(characteristic)].result());
            }
        }

        isFinished = true;
        Set<String> datasetIds = Arrays.stream(datasets)
                .map(Dataset::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // collect summary by preprocessor
        Map<String, SetPreprocessorSummary> preprocResult = procs.stream()
                .collect(Collectors.toMap(SetPreprocessor::id,
                        p -> {
                            TIntObjectHashMap<List<SetPreprocessorStat>> stat = p.getStat();
                            List<String> datasetIdsActual;
                            if (datasets.length == 1 && datasets[0] instanceof OverlapDataset) {
                                datasetIdsActual = ((OverlapDataset<T>) datasets[0]).datasetIds;
                            } else {
                                datasetIdsActual = Arrays
                                        .stream(datasets)
                                        .map(Dataset::id)
                                        .collect(Collectors.toList());
                            }
                            Map<String, List<SetPreprocessorStat>> r = new HashMap<>();
                            TIntObjectIterator<List<SetPreprocessorStat>> it = stat.iterator();
                            while (it.hasNext()) {
                                it.advance();
                                r.put(datasetIdsActual.get(it.key()), it.value());
                            }
                            return new SetPreprocessorSummary(r);
                        }));
        return PostanalysisResult.create(datasetIds, result, preprocResult);
    }
}
