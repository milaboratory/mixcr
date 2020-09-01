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

/**
 *
 */
public class OutputTable {
    public final String name;
    public final List<String> rowNames;
    public final List<String> colNames;
    public final List<OutputTableCell> cells;

    public OutputTable(String name, List<String> rowNames, List<String> colNames, List<OutputTableCell> cells) {
        this.name = name;
        this.rowNames = rowNames;
        this.colNames = colNames;
        this.cells = cells;
    }

    private List<HierarchyNode> columnsHierarchy, rowsHierarchy;

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

    public double[][] columns(double naValue, double nan) {
        int nRows = rowNames.size(), nCols = colNames.size();
        double[][] r = new double[nCols][nRows];
        if (naValue != 0.0)
            for (double[] doubles : r)
                Arrays.fill(doubles, naValue);

        for (OutputTableCell cell : cells)
            r[cell.iCol][cell.iRow] = Double.isNaN(cell.value) ? nan : cell.value;

        return r;
    }

    public Double[][] columns() {
        int nRows = rowNames.size(), nCols = colNames.size();
        Double[][] r = new Double[nCols][nRows];
        for (OutputTableCell cell : cells)
            r[cell.iCol][cell.iRow] = cell.value;

        return r;
    }

    public double[][] rows(double naValue, double nan) {
        int nRows = rowNames.size(), nCols = colNames.size();
        double[][] r = new double[nRows][nCols];
        if (naValue != 0.0)
            for (double[] doubles : r)
                Arrays.fill(doubles, naValue);

        for (OutputTableCell cell : cells)
            r[cell.iRow][cell.iCol] = Double.isNaN(cell.value) ? nan : cell.value;

        return r;
    }

    public Double[][] rows() {
        int nRows = rowNames.size(), nCols = colNames.size();
        Double[][] r = new Double[nRows][nCols];
        for (OutputTableCell cell : cells)
            r[cell.iRow][cell.iCol] = cell.value;

        return r;
    }

    public double minValue() {
        return cells.stream().mapToDouble(c -> c.value).filter(v -> !Double.isNaN(v)).min().orElse(Double.NaN);
    }

    public double maxValue() {
        return cells.stream().mapToDouble(c -> c.value).filter(v -> !Double.isNaN(v)).max().orElse(Double.NaN);
    }

    public OutputTable setRowNames(List<String> rowNames) {
        OutputTable t = new OutputTable(name, rowNames, colNames, cells);
        t.columnsHierarchy = columnsHierarchy;
        t.rowsHierarchy = rowsHierarchy;
        return t;
    }

    public OutputTable setColNames(List<String> colNames) {
        OutputTable t = new OutputTable(name, rowNames, colNames, cells);
        t.columnsHierarchy = columnsHierarchy;
        t.rowsHierarchy = rowsHierarchy;
        return t;
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
            for (String column : colNames) {
                writer.write(sep);
                writer.write(column);
            }

            for (int iRow = 0; iRow < rowNames.size(); ++iRow) {
                writer.write("\n");
                writer.write(rowNames.get(iRow));
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
