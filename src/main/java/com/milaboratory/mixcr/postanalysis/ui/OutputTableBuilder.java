package com.milaboratory.mixcr.postanalysis.ui;

import java.util.*;

/**
 *
 */
final class OutputTableBuilder {
    final String name;
    final List<OutputTableCell> cells = new ArrayList<>();
    final Map<Integer, String> rows = new HashMap<>();
    final Map<Integer, String> cols = new HashMap<>();

    public OutputTableBuilder(String name) {
        this.name = name;
    }

    int nRows = 0, nCols = 0;

    synchronized void add(Coordinates cords, CharacteristicGroupResultCell<?> original) {
        cells.add(new OutputTableCell(cords.iRow, cords.iCol, original.value));
        rows.put(cords.iRow, cords.row.toString());
        cols.put(cords.iCol, cords.col.toString());
        nRows = Math.max(cords.iRow + 1, nRows);
        nCols = Math.max(cords.iCol + 1, nCols);
    }

    OutputTable build() {
        String[] rows = new String[nRows];
        String[] cols = new String[nCols];
        for (int i = 0; i < nRows; ++i) {
            String r = this.rows.get(i);
            Objects.requireNonNull(r);
            rows[i] = r;
        }
        for (int i = 0; i < nCols; ++i) {
            String c = this.cols.get(i);
            Objects.requireNonNull(c);
            cols[i] = c;
        }

        return new OutputTable(name, Arrays.asList(rows), Arrays.asList(cols), cells);
    }
}
