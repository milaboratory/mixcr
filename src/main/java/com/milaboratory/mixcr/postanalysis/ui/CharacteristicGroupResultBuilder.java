package com.milaboratory.mixcr.postanalysis.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public final class CharacteristicGroupResultBuilder<K> {
    private final CharacteristicGroup<K, ?> group;
    private final Set<String> datasetIds;
    private final LinkedHashSet<K> keys = new LinkedHashSet<>();
    private final List<CharacteristicGroupResultCell<K>> cells = new ArrayList<>();

    public CharacteristicGroupResultBuilder(CharacteristicGroup<K, ?> group, Set<String> datasetIds) {
        this.group = group;
        this.datasetIds = datasetIds;
    }

    public CharacteristicGroupResultBuilder<K> add(K key, String datasetId, Double value) {
        if (value == null)
            return this;
        keys.add(key);
        cells.add(new CharacteristicGroupResultCell<>(key, value, datasetId));
        return this;
    }

    public CharacteristicGroupResult<K> build() {
        return new CharacteristicGroupResult<>(group, datasetIds, keys, cells);
    }
}
