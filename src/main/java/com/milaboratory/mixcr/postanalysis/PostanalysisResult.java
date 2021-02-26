package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResultBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class PostanalysisResult {
    /** All dataset ids that were analyzed */
    @JsonProperty("datasetIds")
    private final Set<String> datasetIds;
    /** Characteristic Id -> characteristic data */
    @JsonProperty("data")
    private final Map<String, Array2d> data;

    @JsonCreator
    public PostanalysisResult(@JsonProperty("datasetIds") Set<String> datasetIds,
                              @JsonProperty("data") Map<String, Array2d> data) {
        this.datasetIds = datasetIds;
        this.data = data;
    }

    static <T> PostanalysisResult create(Set<String> datasetIds,
                                         Map<Characteristic<?, T>, Map<String, MetricValue<?>[]>> data) {
        Map<String, Array2d> d = new HashMap<>();
        for (Map.Entry<Characteristic<?, T>, Map<String, MetricValue<?>[]>> e : data.entrySet())
            d.put(e.getKey().name, new Array2d(e.getValue()));
        return new PostanalysisResult(datasetIds, d);
    }

    /** cached results for char groups */
    private final Map<CharacteristicGroup<?, ?>, CharacteristicGroupResult<?>> cached = new IdentityHashMap<>();

    /** project result on a specific char group */
    @SuppressWarnings({"unchecked"})
    public <K, T> CharacteristicGroupResult<K> getTable(CharacteristicGroup<K, T> group) {
        CharacteristicGroupResult<?> r = cached.get(group);
        if (r != null)
            return (CharacteristicGroupResult<K>) r;

        CharacteristicGroupResultBuilder<K> builder = new CharacteristicGroupResultBuilder<>(group, datasetIds);
        for (Characteristic<K, T> ch : group.characteristics) {
            Map<String, MetricsArray> values = data.get(ch.name).data;
            for (Map.Entry<String, MetricsArray> e : values.entrySet()) {
                for (MetricValue<K> val : (MetricValue<K>[]) e.getValue().data)
                    builder.add(val.key, e.getKey(), val.value);
            }
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
        return Objects.equals(datasetIds, that.datasetIds) && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetIds, data);
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
        /** Dataset Id -> Metric values */
        @JsonProperty("2darray")
        private final Map<String, MetricsArray> data;

        @JsonCreator
        Array2d(@JsonProperty("2darray") Map<String, MetricValue<?>[]> data) {
            this.data = data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new MetricsArray(e.getValue())));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Array2d array2d = (Array2d) o;
            return Objects.equals(data, array2d.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }

        @Override
        public String toString() {
            return data.toString();
        }
    }

    private static final class MetricsArray {
        @JsonValue
        private final MetricValue<?>[] data;

        public MetricsArray(MetricValue<?>[] data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MetricsArray that = (MetricsArray) o;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }
}
