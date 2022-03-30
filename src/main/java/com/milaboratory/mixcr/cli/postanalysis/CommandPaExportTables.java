package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupOutputExtractor;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.OutputTable;
import picocli.CommandLine;

@CommandLine.Command(name = "tables",
        sortOptions = false,
        separator = " ",
        description = "Biophysics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype, Overlap")
public final class CommandPaExportTables extends CommandPaExportTablesBase {
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
