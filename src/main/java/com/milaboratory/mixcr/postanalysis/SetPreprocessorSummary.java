package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;


public class SetPreprocessorSummary {
    /** SampleId -> (Preproc -> Stat) */
    @JsonProperty("result")
    public final Map<String, List<SetPreprocessorStat>> result;

    @JsonCreator
    public SetPreprocessorSummary(@JsonProperty("result") Map<String, List<SetPreprocessorStat>> result) {
        this.result = result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SetPreprocessorSummary that = (SetPreprocessorSummary) o;
        return Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result);
    }

    public static void writeToCSV(Path file,
                                  Map<String, SetPreprocessorSummary> preprocSummary,
                                  String sep) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<String, SetPreprocessorSummary> e : preprocSummary.entrySet()) {
            String preprocId = e.getKey();
            SetPreprocessorSummary chSummay = e.getValue();
            for (Map.Entry<String, List<SetPreprocessorStat>> ee : chSummay.result.entrySet()) {
                String sample = ee.getKey();
                List<SetPreprocessorStat> stats = ee.getValue();
                if (stats == null)
                    continue;
                List<Object> row = new ArrayList<>();
                row.add(sample);
                row.add(preprocId);
                addStat(row, SetPreprocessorStat.cumulative(stats));
                for (SetPreprocessorStat stat : stats) {
                    row.add(stat.preprocId);
                    addStat(row, stat);
                }
                rows.add(row);
            }
        }

        int nCols = rows.stream().mapToInt(List::size).max().orElse(0);
        for (List<Object> row : rows) {
            if (row.size() < nCols)
                row.addAll(Collections.nCopies(nCols - row.size(), ""));
        }

        List<String> header = new ArrayList<>();
        header.add("sample");
        for (int i = 0; i < (nCols - 1) / 5; i++) {
            String suff;
            if (i == 0)
                suff = "";
            else
                suff = "#" + i;

            header.add("preprocessor" + suff);
            header.add("nElementsBefore" + suff);
            header.add("sumWeightBefore" + suff);
            header.add("nElementsAfter" + suff);
            header.add("sumWeightAfter" + suff);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE)) {
            for (int i = 0; ; i++) {
                writer.write(header.get(i));
                if (i == header.size() - 1)
                    break;
                writer.write(sep);
            }
            writer.write("\n");

            for (int i = 0; ; i++) {
                for (int j = 0; ; j++) {
                    writer.write(rows.get(i).get(j).toString());
                    if (j == rows.get(i).size() - 1)
                        break;
                    writer.write(sep);
                }
                if (i == rows.size() - 1)
                    break;
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addStat(List<Object> row, SetPreprocessorStat stat) {
        if (stat.dropped) {
            for (int i = 0; i < 4; i++) {
                row.add("na");
            }
        } else {
            row.add(stat.nElementsBefore);
            row.add(stat.sumWeightBefore);
            row.add(stat.nElementsAfter);
            row.add(stat.sumWeightAfter);
        }
    }
}
