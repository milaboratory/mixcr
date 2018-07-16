package com.milaboratory.mixcr.cli;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 *
 */
public class UberActionTest {
//    @Test
//    public void test1() {
//        String[] args = {"--species", "hs",
//                "/Users/poslavskysv/Projects/milab/temp/assss/test_R1.fastq",
//                "/Users/poslavskysv/Projects/milab/temp/assss/test_R2.fastq",
//                "hui"};
//
//        UberAction.UberActionParameters p = new UberAction.UberActionParameters();
//
//        JCommander jCommander = new JCommander(p);
//        jCommander.setAcceptUnknownOptions(true);
//        jCommander.parse(args);
//
//        ActionExportClones.CloneExportParameters ep = p.mkExportParameters("u", "o");
//        ActionExportClones export = new ActionExportClones(ep);
//        Assert.assertTrue(true);
//
//        jCommander.usage();
//    }

    @Test
    public void testUsage1() throws Exception {
        ByteArrayOutputStream by = new ByteArrayOutputStream(1000 * 1024);
        PrintStream ps = new PrintStream(new BufferedOutputStream(by));
        System.setErr(ps);
        Main.main(new UberAction.UberRepSeq().command(), "-h");
        ps.close();
        String str = by.toString();
        Assert.assertFalse(str.contains("--assemblePartial"));
        Assert.assertFalse(str.contains("--extend"));
        Assert.assertFalse(str.contains("--assemble-partial-rounds"));
        Assert.assertFalse(str.contains("--do-extend-alignments"));
    }

    @Test
    public void testUsage2() throws Exception {
        ByteArrayOutputStream by = new ByteArrayOutputStream(1000 * 1024);
        PrintStream ps = new PrintStream(new BufferedOutputStream(by));
        System.setErr(ps);
        Main.main(new UberAction.UberRnaSeq().command(), "-h");
        ps.close();
        String str = by.toString();
        Assert.assertTrue(str.contains("--assemblePartial"));
        Assert.assertTrue(str.contains("--extend"));
        Assert.assertTrue(str.contains("--assemble-partial-rounds"));
        Assert.assertTrue(str.contains("--do-extend-alignments"));
    }
}