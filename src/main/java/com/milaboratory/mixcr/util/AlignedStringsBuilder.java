/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
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
