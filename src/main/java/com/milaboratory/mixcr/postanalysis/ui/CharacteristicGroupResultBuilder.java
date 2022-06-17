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
