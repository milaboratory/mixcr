package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public final class CharacteristicGroupResult<K> {
    /** The group */
    public final CharacteristicGroup<K, ?> group;
    /** All keys presented in the table */
    public final List<K> keys;
    /** Cells */
    public final List<CharacteristicGroupResultCell<K>> cells;
    /** Number of samples */
    public final int nSamples;
    /** Sample names */
    public final List<String> sampleIds;

    public CharacteristicGroupResult(CharacteristicGroup<K, ?> group, List<K> keys, List<CharacteristicGroupResultCell<K>> cells, int nSamples, List<String> sampleIds) {
        this.group = group;
        this.keys = keys;
        this.cells = cells;
        this.nSamples = nSamples;
        this.sampleIds = sampleIds;
    }

    @Override
    public String toString() {
        return cells.toString();
    }

    private Map<Object, OutputTable> outputs;

    public synchronized Map<Object, OutputTable> getOutputs() {
        if (outputs == null)
            outputs = group.views.stream()
                    .flatMap(v -> v.getTables(this).entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return outputs;
    }
}
