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
package com.milaboratory.mixcr.export;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.cli.Util;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerAligner;
import com.milaboratory.mixcr.partialassembler.VDJCMultiRead;
import com.milaboratory.mixcr.tests.MiXCRTestUtils;
import com.milaboratory.mixcr.tests.TargetBuilder;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import io.repseq.core.*;
import org.apache.commons.math3.random.Well44497b;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ListIterator;

public class FieldExtractorsTest {
    @Test
    public void testAnchorPoints1() throws Exception {
        final boolean print = false;
        final Well44497b rg = new Well44497b(12312);

        final VDJCAlignerParameters rnaSeqParams = VDJCParametersPresets.getByName("rna-seq");
        final PartialAlignmentsAssemblerAligner aligner = new PartialAlignmentsAssemblerAligner(rnaSeqParams);

        final VDJCLibrary lib = VDJCLibraryRegistry.getDefault().getLibrary("default", "hs");

        for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes())
            if (gene.isFunctional())
                aligner.addGene(gene);

        final TargetBuilder.VDJCGenes genes = new TargetBuilder.VDJCGenes(lib,
                "TRBV12-3*00", "TRBD1*00", "TRBJ1-3*00", "TRBC2*00");

        //                                 | 310  | 338   | 438
        // 250V + 60CDR3 (20V 7N 10D 3N 20J) + 28J + 100C + 100N
        // "{CDR3Begin(-250)}V*270 NNNNNNN {DBegin(0)}D*10 NNN {CDR3End(-20):FR4End} {CBegin}C*100 N*100"

        final FieldExtractors.ExtractDefaultReferencePointsPositions extractor = new FieldExtractors.ExtractDefaultReferencePointsPositions();

        F6 goAssert = new F6() {
            @Override
            public Integer[][] go(String seq, int len, int offset1, int offset2, int offset3, String expected) {
                final NucleotideSequence baseSeq = TargetBuilder.generateSequence(genes, seq, rg);
                NucleotideSequence seq1 = baseSeq.getRange(offset1, Math.min(baseSeq.size(), offset1 + len));
                NucleotideSequence seq2 = offset2 == -1 ? null : baseSeq.getRange(offset2, Math.min(baseSeq.size(), offset2 + len));
                NucleotideSequence seq3 = offset3 == -1 ? null : baseSeq.getRange(offset3, Math.min(baseSeq.size(), offset3 + len));

                VDJCAlignmentResult<VDJCMultiRead> alignment = offset3 == -1 ?
                        offset2 == -1 ?
                                aligner.process(MiXCRTestUtils.createMultiRead(seq1)) :
                                aligner.process(MiXCRTestUtils.createMultiRead(seq1, seq2)) :
                        aligner.process(MiXCRTestUtils.createMultiRead(seq1, seq2, seq3));
                VDJCAlignments al = alignment.alignment;
                Assert.assertNotNull(al);

                if (print) {
                    MiXCRTestUtils.printAlignment(al);
                    System.out.println();
                    System.out.println("-------------------------------------------");
                    System.out.println();
                }

                String val = extractor.extract(al);

                if (print)
                    System.out.println(val);

                String[] spl = val.split(",");
                Integer[][] result = new Integer[spl.length][ReferencePoint.DefaultReferencePoints.length];
                for (int i = 0; i < spl.length; i++) {
                    String[] spl1 = spl[i].split(":");
                    for (int j = 0; j < spl1.length; j++) {
                        try {
                            result[i][j] = Integer.decode(spl1[j]);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
                return result;
            }
        };

        // No PSegments, just deletions

        Integer[][] r = goAssert.go("{CDR3Begin(-250):VEnd(-3)} 'CCAAA' {DBegin(0):DEnd(0)} 'AAA' {JBegin(2):FR4End} " +
                        "{CBegin}C*100 N*100",
                100, 230, 307, 450, "");
        assertExportPoint(r[0], ReferencePoint.VEnd, -3);
        assertExportPoint(r[0], ReferencePoint.DBegin, 0);
        assertExportPoint(r[0], ReferencePoint.DEnd, 0);
        assertExportPoint(r[0], ReferencePoint.JBegin, -2);

        r = goAssert.go("{CDR3Begin(-250):VEnd(0)} 'CCAAA' {DBegin(0):DEnd(-2)} 'AAA' {JBegin:FR4End} {CBegin}C*100 N*100",
                100, 240, 307, 450, "");
        assertExportPoint(r[0], ReferencePoint.VEnd, 0);
        assertExportPoint(r[0], ReferencePoint.DBegin, 0);
        assertExportPoint(r[0], ReferencePoint.DEnd, -2);
        assertExportPoint(r[0], ReferencePoint.JBegin, 0);

        // With PSegments

        r = goAssert.go("{CDR3Begin(-250):VEnd(0)} {VEnd:VEnd(-3)} 'CCAAA' {DBegin(3):DBegin} {DBegin:DEnd(-2)} 'AAA' " +
                        "{JBegin(2):JBegin} {JBegin:FR4End} {CBegin}C*100 N*100",
                100, 240, 307, 450, "");
        assertExportPoint(r[0], ReferencePoint.VEnd, 3);
        assertExportPoint(r[0], ReferencePoint.DBegin, 3);
        assertExportPoint(r[0], ReferencePoint.DEnd, -2);
        assertExportPoint(r[0], ReferencePoint.JBegin, 2);
    }

    static void assertExportPoint(Integer[] r, ReferencePoint rp, Integer value) {
        for (int i = 0; i < ReferencePoint.DefaultReferencePoints.length; i++)
            if (ReferencePoint.DefaultReferencePoints[i].equals(rp)) {
                Assert.assertEquals(value, r[i]);
                return;
            }
        Assert.fail();
    }

    public interface F6 {
        Integer[][] go(String seq, int len, int offset1, int offset2, int offset3, String expected);
    }

    @Ignore
    @Test
    public void bestHits() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Hit\",\n" +
                    "            header = \"Best " + type + " hit\",\n" +
                    "            description = \"Export best " + type + " hit\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_BEST_" + u + "_HIT = extractBestHit(GeneType." + type + ");");
            System.out.println();
        }
    }

    @Ignore
    @Test
    public void hits() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Hits\",\n" +
                    "            header = \"" + type + " hits\",\n" +
                    "            description = \"Export " + type + " hits\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_" + u + "_HITS = extractHits(GeneType." + type + ");");
            System.out.println();
        }
    }

    @Ignore
    @Test
    public void bestAlignments() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Alignment\",\n" +
                    "            header = \"Best " + type + " alignment\",\n" +
                    "            description = \"Export best " + type + " alignment\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_BEST_" + u + "_ALIGNMENT = extractBestAlignments(GeneType." + type + ");");
            System.out.println();
        }
    }

    @Ignore
    @Test
    public void alignments() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Alignments\",\n" +
                    "            header = \"" + type + " alignments\",\n" +
                    "            description = \"Export " + type + " alignments\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_" + u + "_ALIGNMENTS = extractAlignments(GeneType." + type + ");");
            System.out.println();
        }
    }

    //@Test
    //public void testDescription() throws Exception {
    //    ArrayList<String>[] description = FieldExtractors.getDescription(Clone.class);
    //    System.out.println(Util.printTwoColumns(description[0], description[1], 15, 40, 10, "\n"));
    //}

    @Ignore
    @Test
    public void testName() throws Exception {
        for (Class clazz : Arrays.asList(VDJCObject.class, VDJCAlignments.class, Clone.class)) {
            try (FileOutputStream out = new FileOutputStream(new File("doc/ExportFields" + clazz.getSimpleName() + ".rst"))) {
                out.write(printDocumentation(clazz).getBytes());
            }
        }
    }

    public static String printDocumentation(Class clazz) {
        ArrayList<String>[] cols = FieldExtractors.getDescriptionSpecificForClass(clazz);
        cols[0].add(0, "Field name");
        cols[1].add(0, "Description");
        ListIterator<String>[] iterators = new ListIterator[]{cols[0].listIterator(), cols[1].listIterator()};
        int max = Integer.MIN_VALUE, min = Integer.MIN_VALUE;
        int maxLeftLength = Integer.MIN_VALUE;
        while (iterators[0].hasNext()) {
            String left = iterators[0].next();
//            left = left.trim();
            if (!left.contains("Field name")) {
                left = "``" + left.replaceFirst(" ", "`` ");
                left = left.replace("<", "``<");
                left = left.replace(">", ">``");
            }
            iterators[0].set(left = "| " + remEx(left));
            maxLeftLength = Math.max(maxLeftLength, left.length());
            String right = iterators[1].next();
            iterators[1].set(right = "| " + remEx(right));
            max = Math.max(max, right.length());
            min = Math.min(min, right.length());
        }
        ListIterator<String> it = cols[1].listIterator();
        while (it.hasNext()) {
            String next = it.next();
            it.set(next + zeros(max - next.length()) + "    |");
        }
        String result = Util.printTwoColumns(cols[0], cols[1], maxLeftLength + 10, 5000, 2);
        String[] split = result.split("\\n")[0].split("\\|");
        String left = chars(split[1].length(), '-');
        String right = chars(split[2].length(), '-');
        String separator = "+" + left + "+" + right + "+";
        String headerSeparator = separator.replace("-", "=");
        StringBuilder sb = new StringBuilder();
        sb.append(separator).append("\n");
        result = result.replaceFirst("\\|\\n", "|\n" + headerSeparator + "\n");
        result = result.replaceAll("\\|\\n\\|", "|\n" + separator + "\n|");
        result = result.replaceAll("^\\s*$", "");
        result = result.substring(0, result.length() - 1);
        sb.append(result).append("\n");
        sb.append(separator).append("\n");
        return sb.toString();
    }

    static String zeros(int l) {
        return chars(l, ' ');
    }

    private static String chars(int n, char cc) {
        char[] c = new char[n];
        Arrays.fill(c, cc);
        return String.valueOf(c);
    }

    static String remEx(String str) {
        String replace = str.trim().replace("Export", "").trim();
        replace = String.valueOf(replace.charAt(0)).toUpperCase() + replace.substring(1);
        return replace;
    }
}