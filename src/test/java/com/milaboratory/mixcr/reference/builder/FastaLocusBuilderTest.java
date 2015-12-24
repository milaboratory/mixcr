/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
package com.milaboratory.mixcr.reference.builder;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.reference.*;
import com.milaboratory.mixcr.reference.builder.FastaLocusBuilderParameters.AnchorPointPositionInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static com.milaboratory.mixcr.reference.ReferencePoint.*;

/**
 * Created by dbolotin on 25/10/15.
 */
public class FastaLocusBuilderTest {
    @Test
    public void test1V() throws Exception {
        FastaLocusBuilderParameters parameters =
                new FastaLocusBuilderParameters(GeneType.Variable,
                        "^[^\\|]+\\|([^\\|]+)",
                        "^[^\\|]+\\|[^\\|]+\\|[^\\|]+\\|[\\(\\[]?F",
                        "^[^\\|]+\\|[^\\|]+\\*01", '.',
                        FR1Begin, true,
                        new AffineGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 1, -4, -11, -2),
                        new AnchorPointPositionInfo(FR1Begin, 0),
                        new AnchorPointPositionInfo(CDR1Begin, 78),
                        new AnchorPointPositionInfo(FR2Begin, 114),
                        new AnchorPointPositionInfo(CDR2Begin, 165),
                        new AnchorPointPositionInfo(FR3Begin, 195),
                        new AnchorPointPositionInfo(CDR3Begin, 309),
                        new AnchorPointPositionInfo(VEnd, AnchorPointPositionInfo.END_OF_SEQUENCE));

        FastaLocusBuilder builder = new FastaLocusBuilder(Locus.IGH, parameters).noExceptionOnError();
        builder.importAllelesFromFile("/Volumes/Data/Projects/MiLaboratory/tmp/result/human_IGHV.fasta");
        builder.compile();
        builder.printReport();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LociLibraryWriter writer = new LociLibraryWriter(bos);
        writer.writeMagic();
        writer.writeBeginOfLocus(Species.HomoSapiens, Locus.IGH);
        builder.writeAlleles(writer);
        writer.writeEndOfLocus();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        LociLibrary ll = LociLibraryReader.read(bis, false);
        NucleotideSequence expected = new NucleotideSequence("ATCAACTGGGTGCGACAGGCCACTGGACAAGGGCTTGAGTGGATGGGATGG");
        Allele allele = ll.getAllele(Species.HomoSapiens, "IGHV1-8*01");
        Assert.assertEquals(expected, allele.getFeature(GeneFeature.FR2));
        expected = new NucleotideSequence("GGATACACCTTCACCAGCTATGAT");
        allele = ll.getAllele(Species.HomoSapiens, "IGHV1-8*02");
        Assert.assertEquals(expected, allele.getFeature(GeneFeature.CDR1));
    }

    @Test
    public void test1J() throws Exception {
        FastaLocusBuilderParameters parameters =
                new FastaLocusBuilderParameters(GeneType.Joining,
                        "^[^\\|]+\\|([^\\|]+)",
                        "^[^\\|]+\\|[^\\|]+\\|[^\\|]+\\|[\\(\\[]?F",
                        "^[^\\|]+\\|[^\\|]+\\*01", '.',
                        FR4Begin, true,
                        new AffineGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 1, -4, -21, -2),
                        new AnchorPointPositionInfo(JBegin, 0),
                        new AnchorPointPositionInfo(FR4Begin, -31, "(?:TGG|TT[TC])()GG[ATGC]{4}GG[ATGC]"),
                        new AnchorPointPositionInfo(FR4End, AnchorPointPositionInfo.END_OF_SEQUENCE));

        FastaLocusBuilder builder = new FastaLocusBuilder(Locus.IGH, parameters).noExceptionOnError();
        builder.importAllelesFromFile("/Volumes/Data/Projects/MiLaboratory/tmp/result/human_IGHJ.fasta");
        builder.compile();
        builder.printReport();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LociLibraryWriter writer = new LociLibraryWriter(bos);
        writer.writeMagic();
        writer.writeBeginOfLocus(Species.HomoSapiens, Locus.IGH);
        builder.writeAlleles(writer);
        writer.writeEndOfLocus();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        LociLibrary ll = LociLibraryReader.read(bis, false);
        NucleotideSequence expected = new NucleotideSequence("GGGCAAGGGACCACGGTCACCGTCTCCTCAG");
        Allele allele = ll.getAllele(Species.HomoSapiens, "IGHJ6*01");
        Assert.assertEquals(expected, allele.getFeature(GeneFeature.FR4));
        expected = new NucleotideSequence("GGCAAAGGGACCACGGTCACCGTCTCCTCA");
        allele = ll.getAllele(Species.HomoSapiens, "IGHJ6*03");
        Assert.assertEquals(expected, allele.getFeature(GeneFeature.FR4));
    }

    @Test
    public void test1D() throws Exception {
        FastaLocusBuilderParameters parameters =
                new FastaLocusBuilderParameters(GeneType.Diversity,
                        "^[^\\|]+\\|([^\\|]+)",
                        "^[^\\|]+\\|[^\\|]+\\|[^\\|]+\\|[\\(\\[]?F",
                        "^[^\\|]+\\|[^\\|]+\\*01", '.',
                        null, true,
                        new AffineGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 1, -4, -21, -2),
                        new AnchorPointPositionInfo(DBegin, 0),
                        new AnchorPointPositionInfo(DEnd, AnchorPointPositionInfo.END_OF_SEQUENCE));

        FastaLocusBuilder builder = new FastaLocusBuilder(Locus.IGH, parameters).noExceptionOnError();
        builder.importAllelesFromFile("/Volumes/Data/Projects/MiLaboratory/tmp/result/human_IGHD.fasta");
        builder.compile();
        builder.printReport();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LociLibraryWriter writer = new LociLibraryWriter(bos);
        writer.writeMagic();
        writer.writeBeginOfLocus(Species.HomoSapiens, Locus.IGH);
        builder.writeAlleles(writer);
        writer.writeEndOfLocus();
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        LociLibrary ll = LociLibraryReader.read(bis, false);
        int i = 0;
        NucleotideSequence expected = new NucleotideSequence("GTATTACTATGGTTCGGGGAGTTATTATAAC");
        Allele allele = ll.getAllele(Species.HomoSapiens, "IGHD3-10*01");
        Assert.assertEquals(expected, allele.getFeature(GeneFeature.DRegion));
        expected = new NucleotideSequence("GTATTACTATGTTCGGGGAGTTATTATAAC");
        allele = ll.getAllele(Species.HomoSapiens, "IGHD3-10*02");
        Assert.assertEquals(expected, allele.getFeature(GeneFeature.DRegion));
    }

    private static AminoAcidSequence tr(StringWithMapping sm, int from, int to) {
        String str = sm.getModifiedString();
        int sFrom = sm.convertPosition(from);
        int sTo = to == -1 ? str.length() : sm.convertPosition(to);
        if (sTo == -1 || sFrom == -1)
            return null;
        return AminoAcidSequence.translateFromLeft(new NucleotideSequence(
                str.substring(sFrom, sTo)));
    }
}