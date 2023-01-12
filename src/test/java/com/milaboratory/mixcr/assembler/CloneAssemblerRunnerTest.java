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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReader;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.quality.QualityAggregationType;
import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.tests.MiXCRTestUtils;
import com.milaboratory.mixcr.vdjaligners.*;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static com.milaboratory.mixcr.tests.MiXCRTestUtils.dummyHeader;
import static com.milaboratory.mixcr.tests.MiXCRTestUtils.emptyFooter;

public class CloneAssemblerRunnerTest {
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
        VDJCAlignerParameters alignerParameters = Objects.requireNonNull(VDJCParametersPresets.getByName("default"));
        VDJCAligner aligner = fastqFiles.length == 1 ? new VDJCAlignerS(alignerParameters) : new VDJCAlignerWithMerge(alignerParameters);

        for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes(Chains.IGH))
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
        File vdjcaFile = TempFileManager.newTempFile();
        try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(vdjcaFile)) {
            writer.writeHeader(dummyHeader(), aligner.getUsedGenes());
            for (Object read : CUtils.it(reader)) {
                VDJCAlignments result = aligner.process(((SequenceRead) read).toTuple(), ((SequenceRead) read));
                if (result != null)
                    writer.write(result);
            }
            writer.setFooter(MiXCRTestUtils.emptyFooter());
        }

        AlignmentsProvider alignmentsProvider = new VDJCAlignmentsReader(vdjcaFile);

        LinearGapAlignmentScoring<NucleotideSequence> scoring = new LinearGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 5, -9, -12);
        CloneFactoryParameters factoryParameters = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.8f,
                        scoring, 5),
                new VJCClonalAlignerParameters(0.8f,
                        scoring, 5),
                null,
                new DClonalAlignerParameters(0.85f, 30.0f, 3, scoring)
        );

        CloneAssemblerParameters assemblerParameters = new CloneAssemblerParameters(
                new GeneFeature[]{GeneFeature.CDR3}, 12,
                QualityAggregationType.Average,
                new CloneClusteringParameters(2, 1,  TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, (byte) 20, .8, "2 of 6", (byte) 15, null);

        System.out.println(GlobalObjectMappers.toOneLine(assemblerParameters));

        CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(
                PreCloneReader.fromAlignments(alignmentsProvider, assemblerParameters.getAssemblingFeatures(), __ -> {
                }),
                new CloneAssembler(TagsInfo.NO_TAGS, assemblerParameters, true, aligner.getUsedGenes(), alignerParameters));
        SmartProgressReporter.startProgressReport(assemblerRunner);
        assemblerRunner.run();

        CloneSet cloneSet = assemblerRunner.getCloneSet(dummyHeader(), emptyFooter());

        File tmpClnsFile = TempFileManager.newTempFile();

        try (ClnsWriter writer = new ClnsWriter(tmpClnsFile)) {
            writer.writeCloneSet(cloneSet);
            writer.setFooter(emptyFooter());
        }

        CloneSet cloneSetDeserialized = CloneSetIO.read(tmpClnsFile);

        assertCSEquals(cloneSet, cloneSetDeserialized);

        OutputPort<ReadToCloneMapping> rrr = assemblerRunner.assembler.getAssembledReadsPort();
        ReadToCloneMapping take;
        while ((take = rrr.take()) != null)
            System.out.println(take);

        return cloneSet;
    }

    private static void assertCSEquals(CloneSet expected, CloneSet actual) {
        Assert.assertEquals(expected.getClones().size(), actual.getClones().size());
        Assert.assertEquals(expected.getTotalCount(), actual.getTotalCount(), 0.1);
        Assert.assertArrayEquals(expected.getAssemblingFeatures(), actual.getAssemblingFeatures());

        for (GeneType geneType : GeneType.values())
            Assert.assertEquals(expected.getFeatureToAlign(geneType),
                    actual.getFeatureToAlign(geneType));

        for (int i = 0; i < expected.getClones().size(); ++i)
            Assert.assertEquals(expected.getClones().get(i), actual.getClones().get(i));
    }
}
