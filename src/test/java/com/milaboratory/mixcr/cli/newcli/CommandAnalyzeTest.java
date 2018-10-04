package com.milaboratory.mixcr.cli.newcli;

import com.milaboratory.mixcr.util.RunMiXCR;
import com.milaboratory.util.TempFileManager;
import org.junit.Test;

import java.util.Arrays;

/**
 *
 */
public class CommandAnalyzeTest {

    @Test
    public void test1() {
        for (String name : Arrays.asList("test", "sample_IGH")) {
            String
                    r1 = RunMiXCR.class.getResource("/sequences/" + name + "_R1.fastq").getFile(),
                    r2 = RunMiXCR.class.getResource("/sequences/" + name + "_R2.fastq").getFile();
            String report = TempFileManager.getTempFile().getAbsolutePath();
            String out = TempFileManager.getTempFile().getAbsolutePath();
            Main.parse("analyze", "shotgun",
                    "--source-type", "rna",
                    "--export-germline",
                    "--contig-assembly",
                    "-s", "hs", "-f",
                    "--report", report,
                    r1,
                    r2,
                    out);
        }
    }
}