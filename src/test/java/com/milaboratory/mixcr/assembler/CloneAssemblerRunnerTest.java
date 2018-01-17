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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReader;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.quality.QualityAggregationType;
import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.vdjaligners.*;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class CloneAssemblerRunnerTest {
    @Ignore
    @Test
    public void test1() throws Exception {
        String[] str = {"sequences/sample_IGH_R1.fastq", "sequences/sample_IGH_R2.fastq"};
        CloneSet cloneSet = runFullPipeline(str);
        System.out.println("\n\n");
        for (Clone clone : cloneSet) {
            System.out.println(clone);
            System.out.println(Arrays.toString(clone.getHits(GeneType.Variable)));
            System.out.println(Arrays.toString(clone.getHits(GeneType.Joining)));
            System.out.println(clone.getFeature(GeneFeature.CDR3));
            System.out.println("" +
                    clone.getFeature(GeneFeature.VCDR3Part) +
                    " " + clone.getFeature(GeneFeature.VJJunction) +
                    " " + clone.getFeature(GeneFeature.JCDR3Part));
        }
    }

    private static CloneSet runFullPipeline(String... fastqFiles) throws IOException, InterruptedException {
        //building alignments
        VDJCAlignerParameters alignerParameters = VDJCParametersPresets.getByName("default");
        VDJCAligner aligner = fastqFiles.length == 1 ? new VDJCAlignerS(alignerParameters) : new VDJCAlignerWithMerge(alignerParameters);

        for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("mi", "hs").getGenes(Chains.IGH))
            if (alignerParameters.containsRequiredFeature(gene))
                aligner.addGene(gene);

        SequenceReader reader;
        if (fastqFiles.length == 1)
            reader = new SingleFastqReader(CloneAssemblerRunnerTest.class.getClassLoader().getResourceAsStream(fastqFiles[0]),
                    true);
        else
            reader = new PairedFastqReader(CloneAssemblerRunnerTest.class.getClassLoader().getResourceAsStream(fastqFiles[0]),
                    CloneAssemblerRunnerTest.class.getClassLoader().getResourceAsStream(fastqFiles[1]), true);

        //write alignments to byte array
        ByteArrayOutputStream alignmentsSerialized = new ByteArrayOutputStream();
        try(VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(alignmentsSerialized)) {
            writer.header(aligner);
            for (Object read : CUtils.it(reader)) {
                VDJCAlignmentResult result = (VDJCAlignmentResult) aligner.process((SequenceRead) read);
                if (result.alignment != null)
                    writer.write(result.alignment);
            }
        }

        AlignmentsProvider alignmentsProvider = AlignmentsProvider.Util.createProvider(
                alignmentsSerialized.toByteArray(),
                VDJCLibraryRegistry.getDefault());

        LinearGapAlignmentScoring<NucleotideSequence> scoring = new LinearGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 5, -9, -12);
        CloneFactoryParameters factoryParameters = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.8f,
                        scoring, 5),
                new VJCClonalAlignerParameters(0.8f,
                        scoring, 5),
                null,
                new DAlignerParameters(GeneFeature.DRegion, 0.85f, 30.0f, 3, scoring)
        );

        CloneAssemblerParameters assemblerParameters = new CloneAssemblerParameters(
                new GeneFeature[]{GeneFeature.CDR3}, 12,
                QualityAggregationType.Average,
                new CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, true, false, 0.4, true, (byte) 20, .8, "2 of 6", (byte) 15);

        System.out.println(GlobalObjectMappers.toOneLine(assemblerParameters));

        CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(alignmentsProvider,
                new CloneAssembler(assemblerParameters, true, aligner.getUsedGenes(), alignerParameters), 2);
        SmartProgressReporter.startProgressReport(assemblerRunner);
        assemblerRunner.run();

        CloneSet cloneSet = assemblerRunner.getCloneSet();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CloneSetIO.write(cloneSet, bos);

        CloneSet cloneSetDeserialized = CloneSetIO.readClns(new ByteArrayInputStream(bos.toByteArray()));

        assertCSEquals(cloneSet, cloneSetDeserialized);

        OutputPortCloseable<ReadToCloneMapping> rrr = assemblerRunner.assembler.getAssembledReadsPort();
        ReadToCloneMapping take;
        while ((take = rrr.take()) != null)
            System.out.println(take);

        return cloneSet;
    }

    private static void assertCSEquals(CloneSet expected, CloneSet actual) {
        Assert.assertEquals(expected.getClones().size(), actual.getClones().size());
        Assert.assertEquals(expected.getTotalCount(), actual.getTotalCount());
        Assert.assertArrayEquals(expected.getAssemblingFeatures(), actual.getAssemblingFeatures());

        for (GeneType geneType : GeneType.values())
            Assert.assertEquals(expected.getAlignedGeneFeature(geneType),
                    actual.getAlignedGeneFeature(geneType));

        for (int i = 0; i < expected.getClones().size(); ++i)
            Assert.assertEquals(expected.getClones().get(i), actual.getClones().get(i));
    }
}