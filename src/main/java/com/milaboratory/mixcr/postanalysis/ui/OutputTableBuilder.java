package com.milaboratory.mixcr.postanalysis.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 *
 */
final class OutputTableBuilder {
    final String name;
    final List<OutputTableCell> cells = new ArrayList<>();
    final LinkedHashSet<Object> rows = new LinkedHashSet<>();
    final LinkedHashSet<Object> cols = new LinkedHashSet<>();

    public OutputTableBuilder(String name) {
        this.name = name;
    }

    void add(Coordinates coords, CharacteristicGroupResultCell<?> original) {
        add(coords.iRow, coords.iCol, original);
    }

    synchronized void add(Object iRow, Object iCol, Object value) {
        cells.add(new OutputTableCell(iRow, iCol, value));
        rows.add(iRow);
        cols.add(iCol);
    }

    synchronized void add(Object iRow, Object iCol, CharacteristicGroupResultCell<?> original) {
        add(iRow, iCol, original.value);
    }

    OutputTable build() {
        return new OutputTable(name, new ArrayList<>(rows), new ArrayList<>(cols), cells);
    }
}
