/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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