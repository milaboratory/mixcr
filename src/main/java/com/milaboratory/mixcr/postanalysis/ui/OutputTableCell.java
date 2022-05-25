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
