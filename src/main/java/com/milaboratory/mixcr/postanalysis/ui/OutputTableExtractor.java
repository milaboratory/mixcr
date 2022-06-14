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

import java.util.*;
import java.util.function.Function;

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

    static <K> OutputTableExtractor<K> summary(Function<CharacteristicGroupResultCell<K>, Map<Object, Object>> columnsFunction) {
        final class rowAgg {
            final String datasetId;
            final Map<Object, Object> columns;

            public rowAgg(String datasetId, Map<Object, Object> columns) {
                this.datasetId = datasetId;
                this.columns = columns;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                rowAgg rowAgg = (rowAgg) o;
                return Objects.equals(datasetId, rowAgg.datasetId) && Objects.equals(columns, rowAgg.columns);
            }

            @Override
            public int hashCode() {
                return Objects.hash(datasetId, columns);
            }
        }

        return result -> {
            List<OutputTableCell> cells = new ArrayList<>();
            List<Object> columns = null;
            List<Object> rows = new ArrayList<>();
            for (CharacteristicGroupResultCell<K> cell : result.cells) {
                Map<Object, Object> split = columnsFunction.apply(cell);
                if (columns == null)
                    columns = new ArrayList<>(split.keySet());
                rowAgg row = new rowAgg(cell.datasetId, split);
                rows.add(row);
                for (Map.Entry<Object, Object> e : split.entrySet()) {
                    cells.add(new OutputTableCell(row, e.getKey(), e.getValue()));
                }
            }

            return new OutputTable(result.group.name,
                    rows,
                    columns == null ? Collections.emptyList() : columns, cells)
                    .withRowStringer(c -> ((rowAgg) c).datasetId);
        };
    }
}
