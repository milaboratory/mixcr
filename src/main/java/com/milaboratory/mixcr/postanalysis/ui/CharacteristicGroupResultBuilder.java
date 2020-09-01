package com.milaboratory.mixcr.postanalysis.ui;

import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public final class CharacteristicGroupResultBuilder<K> {
    private final CharacteristicGroup<K, ?> group;
    private final List<String> sampleIds;
    private final TObjectIntHashMap<K> keys = new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
    private final List<K> keyList = new ArrayList<>();
    private final List<CharacteristicGroupResultCell<K>> cells = new ArrayList<>();
    private int nSamples = 0;

    public CharacteristicGroupResultBuilder(CharacteristicGroup<K, ?> group, List<String> sampleIds) {
        this.group = group;
        this.sampleIds = sampleIds;
    }

    public CharacteristicGroupResultBuilder<K> add(K key, int sampleIndex, Double value) {
        if (value == null)
            return this;

        nSamples = Math.max(nSamples, sampleIndex + 1);
        int metricIndex = keys.get(key);
        if (metricIndex < 0) {
            metricIndex = keys.size();
            keys.put(key, metricIndex);
            keyList.add(key);
        }

        cells.add(new CharacteristicGroupResultCell<>(key, value, sampleIndex, metricIndex));
        return this;
    }

    public CharacteristicGroupResult<K> build() {
        return new CharacteristicGroupResult<>(group, keyList, cells, nSamples, sampleIds);
    }
}
