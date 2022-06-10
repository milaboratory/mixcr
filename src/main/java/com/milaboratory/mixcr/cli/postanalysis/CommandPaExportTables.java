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
package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupOutputExtractor;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.OutputTable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "exportTables",
        sortOptions = false,
        separator = " ",
        description = "Biophysics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype, Overlap")
public final class CommandPaExportTables extends CommandPaExportTablesBase {
    public CommandPaExportTables() {}

    public CommandPaExportTables(PaResult paResult, String out) {
        super(paResult, out);
    }

    @Override
    void run1(PaResultByGroup result) {
        for (CharacteristicGroup<?, ?> table : result.schema.tables) {
            writeTables(outExtension(result.group), result.result.getTable(table));
        }
    }

    <K> void writeTables(String extension, CharacteristicGroupResult<K> tableResult) {
        for (CharacteristicGroupOutputExtractor<K> view : tableResult.group.views)
            for (OutputTable t : view.getTables(tableResult).values()) {
                t.writeCSV(outDir(), outPrefix() + ".", separator(), extension);
            }
    }
}
