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
package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.util.RunMiXCR;
import com.milaboratory.util.TempFileManager;
import org.junit.Assert;
import org.junit.Test;
import picocli.CommandLine;

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
            CommandLine.ParseResult p = Main.parseArgs("analyze", "shotgun",
                    "--starting-material", "rna",
                    "--impute-germline-on-export",
                    "--contig-assembly",
                    "-s", "hs", "-f",
                    "--report", report,
                    r1,
                    r2,
                    out).getParseResult();
            Assert.assertFalse(p.isVersionHelpRequested());
            Assert.assertTrue(p.unmatched().isEmpty());
            Assert.assertTrue(p.errors().isEmpty());
        }
    }

    @Test
    public void test2() {
        for (String name : Arrays.asList("test", "sample_IGH")) {
            String
                    r1 = RunMiXCR.class.getResource("/sequences/" + name + "_R1.fastq").getFile(),
                    r2 = RunMiXCR.class.getResource("/sequences/" + name + "_R2.fastq").getFile();
            String report = TempFileManager.getTempFile().getAbsolutePath();
            String out = TempFileManager.getTempFile().getAbsolutePath();
            CommandLine.ParseResult p = Main.parseArgs("analyze", "amplicon",
                    "--starting-material", "rna",
                    "--5-end", "v-primers",
                    "--3-end", "j-primers",
                    "--adapters", "no-adapters",
                    "--impute-germline-on-export",
                    "--contig-assembly",
                    "-s", "hs", "-f",
                    "--report", report,
                    r1,
                    r2,
                    out).getParseResult();
            Assert.assertFalse(p.isVersionHelpRequested());
            Assert.assertTrue(p.unmatched().isEmpty());
            Assert.assertTrue(p.errors().isEmpty());
        }
    }
}