/*
 * Copyright (c) 2014-2016, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.reference;

import io.repseq.reference.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import static io.repseq.reference.GeneFeature.*;

public class LLIOTests {
    @Test
    public void test3ReadLL() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample, false);
        Assert.assertTrue(library.getAllAlleles().size() > 100);
        Allele allele = library.getAllAlleles().iterator().next();
        Assert.assertTrue(allele.getPartitioning() != null);
    }

    @Test
    @Ignore
    public void test3ReadLL1() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample, true);
        for (Allele allele : library.getAllAlleles(Species.HomoSapiens)) {
            if (allele.getName().contains("IGHV3-21*00")) {
                System.out.println(allele.getName());
                //System.out.println(AminoAcidSequence.translate(allele.getFeature(VRegion), 0));
                System.out.println(allele.getFeature(VRegion));
                System.out.println(allele.getFeature(FR3));
                System.out.println(allele.isFunctional());
                //System.out.println(Arrays.toString(allele.getPartitioning().points));
            }
        }
    }

    @Test
    @Ignore
    public void test3ReadLL3() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample, true);
        for (Allele allele : library.getAllAlleles(Species.HomoSapiens)) {
            if (allele.getName().contains("TRBV6-3")) {
                System.out.println(allele.getName());
                //System.out.println(AminoAcidSequence.translate(allele.getFeature(VRegion), 0));
                System.out.println(allele.getFeature(VTranscript).size());
                System.out.println(allele.getFeature(FR3));
                System.out.println(allele.isFunctional());
                //System.out.println(Arrays.toString(allele.getPartitioning().points));
            }
        }
    }

    @Test
    @Ignore
    public void test3ReadLL2() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample, true);
        for (Allele allele : library.getAllAlleles(Species.HomoSapiens)) {
            if (allele.getName().contains("TRDV3")) {
                System.out.println(allele.getName());
                //System.out.println(AminoAcidSequence.translate(allele.getFeature(VRegion), 0));
                System.out.println(allele.getFeature(VRegion));
                System.out.println(allele.getFeature(FR3));
                System.out.println(allele.isFunctional());
                //System.out.println(Arrays.toString(allele.getPartitioning().points));
            }
        }
    }

    @Ignore
    @Test
    public void testExportLL() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample, true);
        for (Chain chain : Chain.values()) {
            LocusContainer container = library.getLocus(Species.HomoSapiens, chain);
            export(chain.name().toLowerCase() + "v.txt", container.getReferenceAlleles(GeneType.Variable),
                    VGene,
                    V5UTR, L1, VIntron, L2,
                    FR1, CDR1, FR2, CDR2,
                    FR3, GermlineVCDR3Part);
            export(chain.name().toLowerCase() + "j.txt", container.getReferenceAlleles(GeneType.Joining),
                    GermlineJCDR3Part, FR4);
            export(chain.name().toLowerCase() + "d.txt", container.getReferenceAlleles(GeneType.Diversity),
                    DRegion);
        }
    }

    public void export(String fileName, List<Allele> alleles, GeneFeature... geneFeatures) throws FileNotFoundException {
        try (PrintStream ps = new PrintStream(fileName)) {
            ps.print("Gene Name");
            for (GeneFeature geneFeature : geneFeatures)
                ps.print("\t" + GeneFeature.encode(geneFeature));
            ps.println();
            for (Allele allele : alleles) {
                ps.print(allele.getGene().getName());
                for (GeneFeature geneFeature : geneFeatures)
                    ps.print("\t" + allele.getFeature(geneFeature));
                ps.println();
            }
        }
    }
}
