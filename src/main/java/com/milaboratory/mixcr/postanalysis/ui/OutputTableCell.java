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

import java.util.Objects;

/**
 *
 */
public final class OutputTableCell {
    /** row & col keys */
    public final Object iRow, iCol;
    public final Object value;

    public OutputTableCell(Object iRow, Object iCol, Object value) {
        this.iRow = iRow;
        this.iCol = iCol;
        this.value = value;
    }

    @Override
    public String toString() {
        return iRow + ":" + iCol + "=" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputTableCell that = (OutputTableCell) o;
        return Objects.equals(iRow, that.iRow) && Objects.equals(iCol, that.iCol) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iRow, iCol, value);
    }
}
