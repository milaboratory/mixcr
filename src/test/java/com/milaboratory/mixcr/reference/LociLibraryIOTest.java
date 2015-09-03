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
package com.milaboratory.mixcr.reference;

import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.milaboratory.mixcr.reference.GeneFeature.*;
import static org.junit.Assert.*;

public class LociLibraryIOTest {
    @Test
    public void test1() throws Exception {
        test(false);
    }

    @Test
    public void test2() throws Exception {
        test(true);
    }

    public void test(boolean compressed) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        UUID uuid = UUID.randomUUID();
        LociLibraryWriter writer = new LociLibraryWriter(bos);
        writer.writeMagic();
        writer.writeMetaInfo("G", "B");
        writer.writeCommonSpeciesName(Species.HomoSapiens, "hsa");
        writer.writeSequencePart("A1", 0, new NucleotideSequence("ATTAGACAATTAGACA"), compressed);
        writer.writeBeginOfLocus(Species.HomoSapiens, Locus.TRB, uuid);
        writer.writeAllele(GeneType.Joining, "TRBJ2-4*01", true, true, "A1", new int[]{1, 5, 8}, null, null);
        writer.writeMetaInfo("C", "D");
        writer.writeEndOfLocus();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());

        //Testing library
        LociLibrary library = LociLibraryReader.read(bis);
        assertEquals("B", library.getProperty("G"));

        //Testing container
        LocusContainer container = library.getLocus("hsa", Locus.TRB);
        assertNotNull(container);
        assertEquals("D", container.getProperty("C"));
        assertEquals(uuid, container.getUUID());

        Gene gene = container.getGenes(GeneType.Joining).get(0);
        assertNotNull(gene);
        gene = container.getGene("TRBJ2-4");
        assertNotNull(gene);
        assertEquals(GeneGroup.TRBJ, gene.getGroup());
        assertEquals(0, gene.getIndex());

        Allele allele = gene.getAlleles().get(0);
        assertNotNull(allele);
        allele = container.getAllele(GeneType.Joining, 0);
        assertNotNull(allele);
        allele = container.getAllele("TRBJ2-4*01");
        assertNotNull(allele);
        assertTrue(allele.isFunctional());
        assertTrue(allele.isReference());

        assertEquals(new NucleotideSequence("TTAG"), allele.getFeature(GermlineJCDR3Part));
        //TODO: uncomment after fix for FR4
        //assertEquals(new NucleotideSequence("ACA"), allele.getFeature(GeneFeature.FR4));
        //assertEquals(new NucleotideSequence("TTAGACA"), allele.getFeature(GeneFeature.JRegion));
    }

    @Test
    public void test3ReadLL() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample);
        Assert.assertTrue(library.allAlleles.size() > 100);
        Allele allele = library.allAlleles.iterator().next();
        Assert.assertTrue(allele.getPartitioning() != null);
    }

    @Test
    public void test3ReadLL1() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample);
        for (Allele allele : library.getAllAlleles(Species.HomoSapiens)) {
            if (allele.getName().contains("3-11")) {
                System.out.println(allele.getName());
                System.out.println(allele.isFunctional());
                System.out.println(Arrays.toString(allele.getPartitioning().points));
            }
        }
    }

    @Ignore
    @Test
    public void testExportLL() throws Exception {
        InputStream sample = LociLibraryReader.class.getClassLoader().getResourceAsStream("reference/mi.ll");
        LociLibrary library = LociLibraryReader.read(sample);
        for (Locus locus : Locus.values()) {
            LocusContainer container = library.getLocus(Species.HomoSapiens, locus);
            export(locus.name().toLowerCase() + "v.txt", container.getReferenceAlleles(GeneType.Variable),
                    VGene,
                    V5UTR, L1, Intron, L2,
                    FR1, CDR1, FR2, CDR2,
                    FR3, GermlineVCDR3Part);
            export(locus.name().toLowerCase() + "j.txt", container.getReferenceAlleles(GeneType.Joining),
                    GermlineJCDR3Part, FR4);
            export(locus.name().toLowerCase() + "d.txt", container.getReferenceAlleles(GeneType.Diversity),
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
