package com.milaboratory.mixcr.postanalysis.ui;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 *
 */
public final class OutputTableCell implements Comparable<OutputTableCell> {
    public final int iRow, iCol;
    public final double value;

    public OutputTableCell(int iRow, int iCol, double value) {
        this.iRow = iRow;
        this.iCol = iCol;
        this.value = value;
    }

    @Override
    public String toString() {
        return iRow + ":" + iCol + "=" + value;
    }

    @Override
    public int compareTo(@NotNull OutputTableCell o) {
        int c = Integer.compare(iRow, o.iRow);
        if (c != 0)
            return c;
        c = Integer.compare(iCol, o.iCol);
        if (c != 0)
            return c;

        return Double.compare(value, o.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputTableCell that = (OutputTableCell) o;
        return iRow == that.iRow &&
                iCol == that.iCol &&
                Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(iRow, iCol, value);
    }
}
