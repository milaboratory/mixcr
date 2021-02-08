package com.milaboratory.mixcr.postanalysis.ui;

import java.util.Objects;

/**
 *
 */
public final class OutputTableCell {
    /** row & col keys */
    public final Object iRow, iCol;
    public final double value;

    public OutputTableCell(Object iRow, Object iCol, double value) {
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
        return Double.compare(that.value, value) == 0 && Objects.equals(iRow, that.iRow) && Objects.equals(iCol, that.iCol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iRow, iCol, value);
    }
}
