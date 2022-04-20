package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.cli.Main;
import org.junit.Ignore;
import org.junit.Test;

public class CommandPaExportTest {
    @Ignore
    @Test
    public void test1() {
        Main.main(
                "exportPa",
                "biophysics",
                "--meta",
                "scratch/metadata.csv",
                "--primary-group",
                "Cat2",
                "scratch/pa/pa.json",
                "scratch/pa/bio.pdf"
        );
    }

    @Ignore
    @Test
    public void test2() {
        Main.main(
                "exportPa",
                "vUsage",
                "--chains",
                "IGH",
                "--color-key",
                "Cat2",
                "--meta",
                "scratch/metadata.csv",
                "scratch/pa/pa.json",
                "scratch/pa/vUsage.pdf"
        );
    }

    //    @Ignore
    @Test
    public void test3() {
        Main.main("exportPa", "overlap",
//                "--width", "2000",
//                "--height", "5000",
                "--chains", "TRA",
                "--filter", "Chain=TRA",
//                "--filter", "CellPopulation=CD4mem",
                "--metadata", "/Users/poslavskysv/Projects/milab/mixcr-test-data/metadata.tsv",
                "--color-key", "x_Tissue", "--color-key", "x_Age(months)", "--color-key", "y_MouseID",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlapPa.json",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlap/overlap.pdf");
    }

    @Test
    public void test4() {
        Main.main("exportPa", "preprocSummary",
//                "--width", "2000",
//                "--height", "5000",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlapPa.json",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/tables/preproc/overlap.tsv");
    }
}