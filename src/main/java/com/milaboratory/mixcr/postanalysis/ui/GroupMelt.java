package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 *
 */
public class GroupMelt<K> implements CharacteristicGroupOutputExtractor<K> {
    @JsonProperty("prefix")
    public final String prefix;
    public final MeltFunction<K> meltFunction;
    public final Supplier<CoordinatesProvider<K>> coordinatesProvider;

    public GroupMelt(String prefix, MeltFunction<K> meltFunction, Supplier<CoordinatesProvider<K>> coordinatesProvider) {
        this.prefix = prefix;
        this.meltFunction = meltFunction;
        this.coordinatesProvider = coordinatesProvider;
    }

    @Override
    public Map<Object, OutputTable> getTables(CharacteristicGroupResult<K> result) {
        Map<String, OutputTableBuilder> outputs = new HashMap<>();
        Map<String, CoordinatesProvider<K>> providers = new HashMap<>();
        for (CharacteristicGroupResultCell<K> cell : result.cells) {
            String key = meltFunction.getName(result, cell);
            OutputTableBuilder builder = outputs.computeIfAbsent(key, s -> new OutputTableBuilder(prefix + s));
            CoordinatesProvider<K> coordinatesProvider = providers.computeIfAbsent(key, __ -> this.coordinatesProvider.get());
            Coordinates coords = coordinatesProvider.getXY(cell);
            builder.add(coords, cell);
        }
        Map<Object, OutputTable> ret = new HashMap<>();
        for (Map.Entry<String, OutputTableBuilder> e : outputs.entrySet())
            ret.put(e.getKey(), e.getValue().build());
        return ret;
    }

    public interface MeltFunction<K> {
        String getName(CharacteristicGroupResult<K> result, CharacteristicGroupResultCell<K> cell);
    }

    interface CoordinatesProvider<K> {
        Coordinates getXY(CharacteristicGroupResultCell<K> key);
    }

    public static final class VJUsageMelt<T> extends GroupMelt<KeyFunctions.VJGenes<T>> {
        public VJUsageMelt() {
            super("vjUsage_",
                    (result, cell) -> cell.datasetId,
                    () -> key -> new Coordinates(key.key.vGene, key.key.jJene));
        }

        @Override
        public boolean equals(Object o1) {
            if (this == o1) return true;
            if (o1 == null || getClass() != o1.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 17;
        }
    }
}
