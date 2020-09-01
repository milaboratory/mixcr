package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;

import java.util.*;
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

    public static abstract class SplitCoordinatesProvider<K> implements CoordinatesProvider<K> {
        final Map<Object, Integer> xs = new HashMap<>(), ys = new HashMap<>();

        abstract Object[] split(K key);

        @Override
        public Coordinates getXY(CharacteristicGroupResultCell<K> key) {
            Object[] split = split(key.key);
            Integer x = xs.get(split[0]);
            if (x == null) {
                x = xs.size();
                xs.put(split[0], x);
            }
            Integer y = ys.get(split[1]);
            if (y == null) {
                y = ys.size();
                ys.put(split[1], y);
            }
            return new Coordinates(x, y, split[0].toString(), split[1].toString());
        }
    }

    public static final class VJUsageMelt<T> extends GroupMelt<KeyFunctions.VJGenes<T>> {
        public VJUsageMelt() {
            super("vj_usage_",
                    (result, cell) -> result.sampleIds.get(cell.sampleIndex),
                    () -> new SplitCoordinatesProvider<KeyFunctions.VJGenes<T>>() {
                        @Override
                        Object[] split(KeyFunctions.VJGenes<T> key) {
                            return new Object[]{key.vGene, key.jJene};
                        }
                    });
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

    public static final class BySample<K> extends GroupMelt<K> {
        public BySample(@JsonProperty("prefix") String prefix) {
            super(prefix,
                    (result, cell) -> result.sampleIds.get(cell.sampleIndex),
                    () -> new SplitCoordinatesProvider<K>() {
                        @Override
                        Object[] split(K key) {
                            return new Object[]{key};
                        }
                    });
        }

        @Override
        public boolean equals(Object o1) {
            if (this == o1) return true;
            if (o1 == null || getClass() != o1.getClass()) return false;
            return Objects.equals(prefix, ((BySample<?>) o1).prefix);
        }

        @Override
        public int hashCode() {
            return 123 + Objects.hashCode(prefix);
        }
    }
}
