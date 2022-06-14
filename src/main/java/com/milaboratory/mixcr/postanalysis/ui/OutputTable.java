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


import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
public class OutputTable {
    public final String name;
    /** row & col ids (names) */
    public final List<?> rowIds, colIds;
    /** rowId -> index in rows list */
    private final Map<Object, Integer> rowId2idx;
    /** colId -> index in columns list */
    private final Map<Object, Integer> colId2idx;
    /** sparse cells */
    public final List<OutputTableCell> cells;
    /** row string representation */
    public Function<Object, String> rowStringer = Object::toString;
    /** column string representation */
    public Function<Object, String> colStringer = Object::toString;
    ;

    public OutputTable(String name,
                       List<?> rowIds,
                       List<?> colIds,
                       List<OutputTableCell> cells) {
        this.name = name;
        this.rowIds = rowIds;
        this.colIds = colIds;
        this.cells = cells;
        this.rowId2idx = IntStream.range(0, rowIds.size()).boxed().collect(Collectors.toMap(rowIds::get, i -> i));
        this.colId2idx = IntStream.range(0, colIds.size()).boxed().collect(Collectors.toMap(colIds::get, i -> i));
    }

    public OutputTable withRowStringer(Function<Object, String> rowStringer) {
        this.rowStringer = rowStringer;
        return this;
    }

    public OutputTable withColStringer(Function<Object, String> colStringer) {
        this.colStringer = colStringer;
        return this;
    }


    public int rowIdx(Object rowId) {
        return rowId2idx.get(rowId);
    }

    public int colIdx(Object colId) {
        return colId2idx.get(colId);
    }

    public OutputTable reorder(List<?> rowIds, List<?> colIds) {
        return new OutputTable(name, rowIds, colIds, cells).withRowStringer(rowStringer).withColStringer(colStringer);
    }

    public double[][] drows(double naValue, double nan) {
        int nRows = rowIds.size(), nCols = colIds.size();
        double[][] r = new double[nRows][nCols];
        if (naValue != 0)
            for (double[] doubles : r)
                Arrays.fill(doubles, naValue);

        for (OutputTableCell cell : cells)
            r[rowIdx(cell.iRow)][colIdx(cell.iCol)] = cell.value == null ? nan : (double) cell.value;

        return r;
    }

    public Object[][] rows(Object naValue, Object nan) {
        int nRows = rowIds.size(), nCols = colIds.size();
        Object[][] r = new Object[nRows][nCols];
        if (naValue != null)
            for (Object[] doubles : r)
                Arrays.fill(doubles, naValue);

        for (OutputTableCell cell : cells)
            r[rowIdx(cell.iRow)][colIdx(cell.iCol)] = cell.value == null ? nan : cell.value;

        return r;
    }

    public Object[][] rows() {
        int nRows = rowIds.size(), nCols = colIds.size();
        Object[][] r = new Object[nRows][nCols];
        for (OutputTableCell cell : cells)
            r[rowIdx(cell.iRow)][colIdx(cell.iCol)] = cell.value;

        return r;
    }

    @Override
    public String toString() {
        return cells.toString();
    }

    public void writeCSV(Path dir) {
        writeCSV(dir, "");
    }

    public void writeCSV(Path dir, String prefix) {
        writeCSV(dir, prefix, ",", ".csv");
    }

    public void writeTSV(Path dir) {
        writeTSV(dir, "");
    }

    public void writeTSV(Path dir, String prefix) {
        writeCSV(dir, prefix, "\t", ".tsv");
    }

    public void writeCSV(Path dir, String prefix, String sep, String ext) {
        Path outName = dir.resolve(prefix + name + ext);
        try (PrintStream writer = new PrintStream(outName.toFile())) {
            writeCSV(writer, sep);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeCSV(PrintStream writer, String sep) {
        Object[][] rows2d = rows();
        writer.print("");
        for (Object column : colIds) {
            writer.print(sep);
            writer.print(colStringer.apply(column));
        }

        for (int iRow = 0; iRow < rowIds.size(); ++iRow) {
            writer.print("\n");
            writer.print(rowStringer.apply(rowIds.get(iRow)));
            Object[] row = rows2d[iRow];
            for (Object val : row) {
                writer.print(sep);
                writer.print(val);
            }
        }
    }
}
