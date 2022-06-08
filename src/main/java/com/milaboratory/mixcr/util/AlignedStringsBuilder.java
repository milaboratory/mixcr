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
package com.milaboratory.mixcr.util;

import com.milaboratory.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AlignedStringsBuilder {
    final List<List<String>> parts = new ArrayList<>();
    int spaceBetweenColumns = 1;

    public AlignedStringsBuilder() {
        this.parts.add(new ArrayList<String>());
    }

    public AlignedStringsBuilder cells(String... cellsContent) {
        List<String> row = parts.get(parts.size() - 1);
        row.addAll(Arrays.asList(cellsContent));
        return this;
    }

    public AlignedStringsBuilder row(String... cellsContent) {
        cells(cellsContent);
        newRow();
        return this;
    }

    public AlignedStringsBuilder newRow() {
        parts.add(new ArrayList<String>());
        return this;
    }

    public AlignedStringsBuilder setSpaceBetweenColumns(int spaceBetweenColumns) {
        this.spaceBetweenColumns = spaceBetweenColumns;
        return this;
    }

    @Override
    public String toString() {
        int cols = 0;
        for (List<String> row : parts)
            cols = Math.max(cols, row.size());
        int[] lengths = new int[cols];
        for (List<String> row : parts) {
            int i = 0;
            for (String cell : row) {
                lengths[i] = Math.max(lengths[i], cell.length());
                ++i;
            }
        }
        for (int i = 0; i < lengths.length; i++)
            lengths[i] += spaceBetweenColumns;

        StringBuilder builder = new StringBuilder();
        for (int k = 0; k < parts.size(); k++) {
            List<String> row = parts.get(k);
            if (k == parts.size() - 1 && row.isEmpty())
                continue;

            int i = 0;
            for (String cell : row) {
                builder.append(cell);
                if (i != row.size() - 1)
                    builder.append(StringUtil.spaces(lengths[i] - cell.length()));
                ++i;
            }
            builder.append("\n");
        }
        return builder.toString();
    }
}
