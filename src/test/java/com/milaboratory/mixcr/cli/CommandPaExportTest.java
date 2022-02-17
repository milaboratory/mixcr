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
                "TRB",
                "--meta",
                "scratch/metadata.csv",
                "scratch/pa/pa.json",
                "scratch/pa/vUsage.pdf"
        );
    }
}