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
    public final Set<String> datasetIds;
    /** Characteristic Id -> characteristic data */
    @JsonProperty("data")
    public final Map<String, ChData> data;
    /** Preprocessor Id -> Preprocessors summary */
    @JsonProperty("preprocSummary")
    public final Map<String, SetPreprocessorSummary> preprocSummary;

    @JsonCreator
    public PostanalysisResult(@JsonProperty("datasetIds") Set<String> datasetIds,
                              @JsonProperty("data") Map<String, ChData> data,
                              @JsonProperty("preprocSummary") Map<String, SetPreprocessorSummary> preprocSummary) {
        this.datasetIds = datasetIds;
        this.data = data;
        this.preprocSummary = preprocSummary;
    }

    static <T> PostanalysisResult create(Set<String> datasetIds,
                                         Map<Characteristic<?, T>, Map<String, MetricValue<?>[]>> data,
                                         Map<String, SetPreprocessorSummary> preprocSummary) {
        Map<String, ChData> d = new HashMap<>();
        for (Map.Entry<Characteristic<?, T>, Map<String, MetricValue<?>[]>> e : data.entrySet())
            d.put(e.getKey().name, new ChData(e.getKey().preprocessor.id(), e.getValue()));
        return new PostanalysisResult(datasetIds, d, preprocSummary);
    }

    /** cached results for char groups */
    private final Map<CharacteristicGroup<?, ?>, CharacteristicGroupResult<?>> cached = new IdentityHashMap<>();

    /** project result on a specific char group */
    public PostanalysisResult forGroup(CharacteristicGroup<?, ?> group) {
        return new PostanalysisResult(
                datasetIds,
                group.characteristics.stream()
                        .map(c -> c.name)
                        .collect(Collectors.toMap(c -> c, data::get)),
                group.characteristics.stream()
                        .map(c -> c.preprocessor.id())
                        .distinct()
                        .collect(Collectors.toMap(c -> c, preprocSummary::get)));
    }

    /**
     * Get preprocessor statistics for specified characteristic for spaecified sample.
     */
    public SetPreprocessorStat getPreprocStat(String charId, String sampleId) {
        String preproc = data.get(charId).preproc;
        List<SetPreprocessorStat> stats = preprocSummary.get(preproc).result.get(sampleId);
        if (stats == null)
            return SetPreprocessorStat.empty(preproc);
        return SetPreprocessorStat.cumulative(stats);
    }

    /** Returns a set of characteristics */
    public Set<String> getCharacteristics() {
        return data.keySet();
    }

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
    public static final class ChData {
        /** Preprocessor Id */
        @JsonProperty("preproc")
        public final String preproc;
        /** Dataset Id -> Metric values */
        @JsonProperty("2darray")
        public final Map<String, MetricsArray> data;

        @JsonCreator
        ChData(@JsonProperty("preproc") String preproc,
               @JsonProperty("2darray") Map<String, MetricValue<?>[]> data) {
            this.preproc = preproc;
            this.data = data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new MetricsArray(e.getValue())));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChData chData = (ChData) o;
            return Objects.equals(data, chData.data);
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

    public static final class MetricsArray {
        @JsonValue
        public final MetricValue<?>[] data;

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
