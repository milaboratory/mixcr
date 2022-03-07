package com.milaboratory.mixcr.cli;

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
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlapPa.json",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlap.pdf");
    }
}