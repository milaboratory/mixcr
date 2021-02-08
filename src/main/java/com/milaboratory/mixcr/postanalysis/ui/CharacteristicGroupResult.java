package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    /** All dataset ids that were analyzed */
    public final Set<String> datasetIds;
    /** All keys presented in the table */
    public final Set<K> keys;
    /** Cells */
    public final List<CharacteristicGroupResultCell<K>> cells;

    public CharacteristicGroupResult(CharacteristicGroup<K, ?> group,
                                     Set<String> datasetIds, Set<K> keys,
                                     List<CharacteristicGroupResultCell<K>> cells) {
        this.group = group;
        this.datasetIds = datasetIds;
        this.keys = keys;
        this.cells = cells;
    }

    @Override
    public String toString() {
        return cells.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CharacteristicGroupResult<?> that = (CharacteristicGroupResult<?>) o;
        return Objects.equals(group, that.group)
                && Objects.equals(datasetIds, that.datasetIds)
                && Objects.equals(keys, that.keys)
                && Objects.equals(cells, that.cells)
                && Objects.equals(getOutputs(), that.getOutputs());
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, datasetIds, keys, cells, getOutputs());
    }

    private Map<Object, OutputTable> outputs;

    /** get all views available for this char group */
    public synchronized Map<Object, OutputTable> getOutputs() {
        if (outputs == null)
            outputs = group.views.stream()
                    .flatMap(v -> v.getTables(this).entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return outputs;
    }
}
