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
package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapCharacteristic;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;


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

/*
    public static void byPreprocToCSV(Path path,
                                      CharacteristicGroup<Clone, ?> chGroup,
                                      Map<String, SetPreprocessorSummary> preprocSummary,
                                      String sep
    ) {
        Set<String> preprocs = chGroup.characteristics
                .stream()
                .map(ch -> ch.preprocessor.id())
                .collect(Collectors.toSet());
        preprocSummary = preprocSummary.entrySet().stream()
                .filter(e -> preprocs.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, List<String>> pp2ch = chGroup.characteristics.stream().collect(Collectors.toMap(
                ch -> ch.preprocessor.id(),
                ch -> Collections.singletonList(ch.name),
                (a, b) -> {
                    List<String> c = new ArrayList<>(a);
                    c.addAll(b);
                    return c;
                }
        ));
        byPreprocToCSV(
                path,
                preprocSummary,
                pp2ch,
                sep);
    }

    public static void byPreprocToCSV(Path path,
                                      Map<String, SetPreprocessorSummary> preprocSummary,
                                      Map<String, List<String>> pp2ch,
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
                if (pp2ch != null)
                    row.add(String.join(",", pp2ch.get(preprocId)));
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
        if (pp2ch != null)
            header.add("characteristics");
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

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE)) {
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
*/

    /**
     * Write preprocessing summary data to CSV file with columns characteristic | samples | ....
     */
    @SuppressWarnings("unchecked")
    public static void byCharToCSV(Path path,
                                   PostanalysisSchema<?> schema,
                                   PostanalysisResult result,
                                   String sep) {
        if (schema.isOverlap)
            overlapByCharToCSV(path, (PostanalysisSchema<OverlapGroup<Clone>>) schema, result, sep);
        else
            individualByCharToCSV(path, result, sep);
    }

    /**
     * Write preprocessing summary data to CSV file with columns characteristic | samples | ....
     */
    @SuppressWarnings("unchecked")
    public static void overlapByCharToCSV(Path path,
                                          PostanalysisSchema<OverlapGroup<Clone>> schema,
                                          PostanalysisResult result,
                                          String sep) {

        Map<Set<OverlapType>, Characteristic<?, OverlapGroup<Clone>>> m = schema.getAllCharacterisitcs()
                .stream()
                .collect(Collectors.toMap(
                        SetPreprocessorSummary::getOverlapTypes,
                        it -> it,
                        (a, b) -> a));

        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<Set<OverlapType>, Characteristic<?, OverlapGroup<Clone>>> eCh : m.entrySet()) {
            String ch = eCh.getKey().stream().map(it -> it.name).collect(Collectors.joining("/"));
            String preproc = eCh.getValue().preprocessor.id();
            SetPreprocessorSummary preprocSummary = result.preprocSummary.get(preproc);
            addRows(rows, ch, preproc, preprocSummary);
        }
        writeTable(rows, path, sep);
    }

    @SuppressWarnings("unchecked")
    private static Set<OverlapType> getOverlapTypes(Characteristic<?, OverlapGroup<Clone>> ch) {
        if (ch instanceof Characteristic.CharacteristicWrapper)
            return getOverlapTypes(((Characteristic.CharacteristicWrapper<?, OverlapGroup<Clone>>) ch).inner);
        else
            return new HashSet<>(Arrays.asList(((OverlapCharacteristic<Clone>) ch).overlapTypes));
    }

    /**
     * Write preprocessing summary data to CSV file with columns characteristic | samples | ....
     */
    public static void individualByCharToCSV(Path path,
                                             PostanalysisResult result,
                                             String sep) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<String, PostanalysisResult.ChData> eCh : result.data.entrySet()) {
            String ch = eCh.getKey();
            String preproc = eCh.getValue().preproc;
            SetPreprocessorSummary preprocSummary = result.preprocSummary.get(preproc);
            addRows(rows, ch, preproc, preprocSummary);
        }
        writeTable(rows, path, sep);
    }

    private static void addRows(List<List<Object>> rows, String ch, String preproc, SetPreprocessorSummary preprocSummary) {
        for (Map.Entry<String, List<SetPreprocessorStat>> ee : preprocSummary.result.entrySet()) {
            String sample = ee.getKey();
            List<SetPreprocessorStat> stats = ee.getValue();
            if (stats == null)
                continue;
            List<Object> row = new ArrayList<>();
            row.add(ch);
            row.add(sample);
            row.add(preproc);
            addStat(row, SetPreprocessorStat.cumulative(stats));
            for (SetPreprocessorStat stat : stats) {
                row.add(stat.preprocId);
                addStat(row, stat);
            }
            rows.add(row);
        }
    }

    private static void writeTable(List<List<Object>> rows,
                                   Path path,
                                   String sep) {
        int nCols = rows.stream().mapToInt(List::size).max().orElse(0);
        for (List<Object> row : rows) {
            if (row.size() < nCols)
                row.addAll(Collections.nCopies(nCols - row.size(), ""));
        }

        List<String> header = new ArrayList<>();
        header.add("characteristic");
        header.add("sample");
        for (int i = 0; i < (nCols - 2) / 5; i++) {
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

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
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
