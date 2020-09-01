package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResultBuilder;

import java.util.*;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class PostanalysisResult {
    @JsonProperty("data")
    private final Map<String, Array2d> data;
    @JsonProperty("sampleIds")
    private final List<String> sampleIds;

    @JsonCreator
    public PostanalysisResult(@JsonProperty("data") Map<String, Array2d> data,
                              @JsonProperty("sampleIds") List<String> sampleIds) {
        this.data = data;
        this.sampleIds = sampleIds;
    }

    public PostanalysisResult setSampleIds(List<String> sampleIds) {
        return new PostanalysisResult(data, sampleIds);
    }

    public static <T> PostanalysisResult create(Map<Characteristic<?, T>, MetricValue<?>[][]> data) {
        Map<String, Array2d> d = new HashMap<>();
        for (Map.Entry<Characteristic<?, T>, MetricValue<?>[][]> e : data.entrySet())
            d.put(e.getKey().name, new Array2d(e.getValue()));
        return new PostanalysisResult(d, null);
    }

    private final Map<CharacteristicGroup<?, ?>, CharacteristicGroupResult<?>> cached = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    public synchronized <K, T> CharacteristicGroupResult<K> getTable(CharacteristicGroup<K, T> group) {
        CharacteristicGroupResult<?> r = cached.get(group);
        if (r != null)
            return (CharacteristicGroupResult<K>) r;

        CharacteristicGroupResultBuilder<K> builder = new CharacteristicGroupResultBuilder<>(group, sampleIds);
        for (Characteristic<K, T> ch : group.characteristics) {
            MetricValue<K>[][] values = (MetricValue<K>[][]) data.get(ch.name).data;
            for (int sampleIndex = 0; sampleIndex < values.length; sampleIndex++)
                for (MetricValue<K> val : values[sampleIndex])
                    builder.add(val.key, sampleIndex, val.value);
        }
        CharacteristicGroupResult<K> res = builder.build();
        cached.put(group, res);
        return res;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostanalysisResult that = (PostanalysisResult) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @Override
    public String toString() {
        return data.toString();
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    private static final class Array2d {
        @JsonProperty("2darray")
        private final MetricValue<?>[][] data;

        @JsonCreator
        Array2d(@JsonProperty("2darray") MetricValue<?>[][] data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Array2d array2d = (Array2d) o;
            return Arrays.deepEquals(data, array2d.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return Arrays.deepToString(data);
        }
    }
}
