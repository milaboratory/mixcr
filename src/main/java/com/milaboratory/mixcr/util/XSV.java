package com.milaboratory.mixcr.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class XSV {
    public static <T> void writeXSVHeaders(PrintStream output, Collection<String> columns, String delimiter) {
        output.println(String.join(delimiter, columns));
    }

    public static <T> void writeXSVBody(PrintStream output, Collection<T> records, Map<String, Function<T, Object>> columns, String delimiter) {
        for (T record : records) {
            output.println(columns.values().stream()
                    .map(column -> Objects.toString(column.apply(record), ""))
                    .collect(Collectors.joining(delimiter))
            );
        }
    }

    public static List<Map<String, String>> readXSV(File input, Collection<String> columns, String delimiter) {
        try {
            List<String> lines = Files.readAllLines(input.toPath());
            if (lines.size() == 0) {
                throw new IllegalArgumentException("no header row");
            }
            if (lines.size() == 1) {
                return Collections.emptyList();
            }
            String header = lines.get(0);
            Map<String, Integer> columnsPositions = new HashMap<>();
            String[] columnsFromFile = header.split(delimiter);
            for (int i = 0; i < columnsFromFile.length; i++) {
                columnsPositions.put(columnsFromFile[i], i);
            }
            for (String column : columns) {
                if (!columnsPositions.containsKey(column)) {
                    throw new IllegalArgumentException("no column with name " + column);
                }
            }

            return lines.subList(1, lines.size() - 1).stream()
                    .map(row -> {
                        String[] cells = row.split(delimiter);
                        Map<String, String> result = new HashMap<>();
                        for (String column : columns) {
                            String cellValue;
                            if (cells.length <= columnsPositions.get(column)) {
                                cellValue = null;
                            } else {
                                cellValue = cells[columnsPositions.get(column)];
                                if (cellValue.equals("")) {
                                    cellValue = null;
                                }
                            }
                            result.put(column, cellValue);
                        }
                        return result;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
