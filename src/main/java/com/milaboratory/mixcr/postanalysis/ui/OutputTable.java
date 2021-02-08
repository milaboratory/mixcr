package com.milaboratory.mixcr.postanalysis.ui;

import com.milaboratory.mixcr.postanalysis.clustering.HierarchicalClustering;
import com.milaboratory.mixcr.postanalysis.clustering.HierarchyNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    public OutputTable(String name, List<?> rowIds, List<?> colIds, List<OutputTableCell> cells) {
        this.name = name;
        this.rowIds = rowIds;
        this.colIds = colIds;
        this.cells = cells;
        this.rowId2idx = IntStream.range(0, rowIds.size()).boxed().collect(Collectors.toMap(rowIds::get, i -> i));
        this.colId2idx = IntStream.range(0, colIds.size()).boxed().collect(Collectors.toMap(colIds::get, i -> i));
    }

    public int rowIdx(Object rowId) {
        return rowId2idx.get(rowId);
    }

    public int colIdx(Object colId) {
        return colId2idx.get(colId);
    }

    private volatile List<HierarchyNode> columnsHierarchy, rowsHierarchy;

    public synchronized List<HierarchyNode> columnsHierarchy() {
        if (columnsHierarchy == null)
            columnsHierarchy = Collections.unmodifiableList(HierarchicalClustering.clusterize(columns(0.0, 0.0), 0.0, HierarchicalClustering::EuclideanDistance));
        return columnsHierarchy;
    }

    public synchronized List<HierarchyNode> rowsHierarchy() {
        if (rowsHierarchy == null)
            rowsHierarchy = Collections.unmodifiableList(HierarchicalClustering.clusterize(rows(0.0, 0.0), 0.0, HierarchicalClustering::EuclideanDistance));
        return rowsHierarchy;
    }

    public OutputTable reorder(List<?> rowIds, List<?> colIds) {
        return new OutputTable(name, rowIds, colIds, cells);
    }

    public double[][] columns(double naValue, double nan) {
        int nRows = rowIds.size(), nCols = colIds.size();
        double[][] r = new double[nCols][nRows];
        if (naValue != 0.0)
            for (double[] doubles : r)
                Arrays.fill(doubles, naValue);

        for (OutputTableCell cell : cells)
            r[colIdx(cell.iCol)][rowIdx(cell.iRow)] = Double.isNaN(cell.value) ? nan : cell.value;

        return r;
    }

    public Double[][] columns() {
        int nRows = rowIds.size(), nCols = colIds.size();
        Double[][] r = new Double[nCols][nRows];
        for (OutputTableCell cell : cells)
            r[colIdx(cell.iCol)][rowIdx(cell.iRow)] = cell.value;

        return r;
    }

    public double[][] rows(double naValue, double nan) {
        int nRows = rowIds.size(), nCols = colIds.size();
        double[][] r = new double[nRows][nCols];
        if (naValue != 0.0)
            for (double[] doubles : r)
                Arrays.fill(doubles, naValue);

        for (OutputTableCell cell : cells)
            r[rowIdx(cell.iRow)][colIdx(cell.iCol)] = Double.isNaN(cell.value) ? nan : cell.value;

        return r;
    }

    public Double[][] rows() {
        int nRows = rowIds.size(), nCols = colIds.size();
        Double[][] r = new Double[nRows][nCols];
        for (OutputTableCell cell : cells)
            r[rowIdx(cell.iRow)][colIdx(cell.iCol)] = cell.value;

        return r;
    }

    public double minValue() {
        return cells.stream().mapToDouble(c -> c.value).filter(v -> !Double.isNaN(v)).min().orElse(Double.NaN);
    }

    public double maxValue() {
        return cells.stream().mapToDouble(c -> c.value).filter(v -> !Double.isNaN(v)).max().orElse(Double.NaN);
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
        Double[][] rows2d = rows();
        Path outName = dir.resolve(prefix + name + ext);
        try (BufferedWriter writer = Files.newBufferedWriter(outName, StandardOpenOption.CREATE)) {
            writer.write("");
            for (Object column : colIds) {
                writer.write(sep);
                writer.write(column.toString());
            }

            for (int iRow = 0; iRow < rowIds.size(); ++iRow) {
                writer.write("\n");
                writer.write(rowIds.get(iRow).toString());
                Double[] row = rows2d[iRow];
                for (Double val : row) {
                    writer.write(sep);
                    writer.write(Double.toString(val == null ? Double.NaN : val));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
