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
