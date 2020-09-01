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
            int i1 = cell.key.i1, i2 = cell.key.i2;
            tab.add(new Coordinates(i1, i2, result.sampleIds.get(i1), result.sampleIds.get(i2)), cell);
            tab.add(new Coordinates(i2, i1, result.sampleIds.get(i2), result.sampleIds.get(i1)), cell);
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
