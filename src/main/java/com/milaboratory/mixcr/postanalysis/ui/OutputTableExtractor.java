package com.milaboratory.mixcr.postanalysis.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 */
interface OutputTableExtractor<K> {
    OutputTable getTable(CharacteristicGroupResult<K> result);

    static <K> OutputTableExtractor<K> summary() {
        return result -> {
            List<OutputTableCell> cells = new ArrayList<>();
            for (CharacteristicGroupResultCell<K> cell : result.cells)
                cells.add(new OutputTableCell(cell.sampleIndex, cell.metricIndex, cell.value));

            return new OutputTable(result.group.name, result.sampleIds,
                    result.keys.stream().map(Objects::toString).collect(Collectors.toList()), cells);
        };
    }
}
