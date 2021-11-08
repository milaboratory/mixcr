package com.milaboratory.mixcr.postanalysis.ui;

import java.util.Collections;
import java.util.Map;

/**
 *
 */
public class GroupSummary<K> implements CharacteristicGroupOutputExtractor<K> {
    public static final String key = "summary";

    @Override
    public Map<Object, OutputTable> getTables(CharacteristicGroupResult<K> result) {
        return Collections.singletonMap(key, OutputTableExtractor.<K>summary().getTable(result));
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
