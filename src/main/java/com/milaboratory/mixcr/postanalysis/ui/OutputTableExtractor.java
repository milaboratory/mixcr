package com.milaboratory.mixcr.postanalysis.ui;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
interface OutputTableExtractor<K> {
    OutputTable getTable(CharacteristicGroupResult<K> result);

    static <K> OutputTableExtractor<K> summary() {
        return result -> {
            List<OutputTableCell> cells = new ArrayList<>();
            for (CharacteristicGroupResultCell<K> cell : result.cells)
                cells.add(new OutputTableCell(cell.datasetId, cell.key, cell.value));

            return new OutputTable(result.group.name,
                    new ArrayList<>(result.datasetIds),
                    new ArrayList<>(result.keys), cells);
        };
    }
}
