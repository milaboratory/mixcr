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

import com.milaboratory.mixcr.postanalysis.overlap.OverlapKey;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class OverlapSummary<K> implements CharacteristicGroupOutputExtractor<OverlapKey<K>> {
    @Override
    public Map<Object, OutputTable> getTables(CharacteristicGroupResult<OverlapKey<K>> result) {
        Map<K, OutputTableBuilder> tables = new HashMap<>();
        for (CharacteristicGroupResultCell<OverlapKey<K>> cell : result.cells) {
            OutputTableBuilder tab = tables.computeIfAbsent(cell.key.key, __ -> new OutputTableBuilder(cell.key.key.toString()));
            tab.add(cell.key.id1, cell.key.id2, cell);
            tab.add(cell.key.id2, cell.key.id1, cell);
        }
        Map<Object, OutputTable> r = new HashMap<>();
        for (Map.Entry<K, OutputTableBuilder> e : tables.entrySet())
            r.put(e.getKey(), e.getValue().build());
        return r;
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
