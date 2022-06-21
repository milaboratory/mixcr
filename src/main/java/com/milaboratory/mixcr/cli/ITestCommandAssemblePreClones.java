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

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.mitool.consensus.AAssemblerParameters;
import com.milaboratory.mitool.consensus.GConsensusAssemblerParameters;
import com.milaboratory.mixcr.assembler.preclone.FilePreCloneReader;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.tag.TagInfo;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagValue;
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.TempFileDest;
import com.milaboratory.util.TempFileManager;
import gnu.trove.iterator.TObjectDoubleIterator;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static picocli.CommandLine.*;

@Command(name = "itestAssemblePreClones",
        separator = " ",
        hidden = true)
public class ITestCommandAssemblePreClones extends ACommandMiXCR {
    @Parameters(arity = "4", description = "input_file output_file output_clones output_alignments")
    public List<String> files;

    @Option(description = "Overlap sequences on the cell level instead of UMIs for tagged data with molecular and cell barcodes",
            names = {"--cell-level"})
    public boolean cellLevel = false;

    @Option(description = "Use system temp folder for temporary files, the output folder will be used if this option is omitted.",
            names = {"--use-system-temp"})
    public boolean useSystemTemp = false;

    private static final AAssemblerParameters aAssemblerParams = AAssemblerParameters.builder()
            .bandWidth(4)
            .scoring(LinearGapAlignmentScoring.getNucleotideBLASTScoring(-14))
            .minAlignmentScore(40)
            .maxAlignmentPenalty(33)
            .trimMinimalSumQuality(20)
            .trimReferenceRegion(false)
            .maxQuality((byte) 45)
            .build();
    private static final GConsensusAssemblerParameters gAssemblerParams = GConsensusAssemblerParameters.builder()
            .aAssemblerParameters(aAssemblerParams)
            .maxIterations(4)
            .minAltSeedQualityScore((byte) 11)
            .minimalRecordShare(0.1)
            .minimalRecordCount(2)
            .build();

    @Override
    public void run0() throws Exception {
        long totalAlignments;
        TempFileDest tmp = TempFileManager.smartTempDestination(files.get(1), "", useSystemTemp);
        int cdr3Hash = 0;
        try (VDJCAlignmentsReader alignmentsReader = new VDJCAlignmentsReader(files.get(0))) {
            totalAlignments = alignmentsReader.getNumberOfAlignments();
            PreCloneAssemblerRunner assemblerRunner = new PreCloneAssemblerRunner(
                    alignmentsReader,
                    cellLevel ? TagType.Cell : TagType.Molecule,
                    new GeneFeature[]{GeneFeature.CDR3},
                    PreCloneAssemblerParameters.DefaultGConsensusAssemblerParameters,
                    Paths.get(files.get(1)),
                    tmp);
            SmartProgressReporter.startProgressReport(assemblerRunner);
            assemblerRunner.run();
            assemblerRunner.getReport().writeReport(ReportHelper.STDOUT);
            for (VDJCAlignments al : CUtils.it(alignmentsReader.readAlignments()))
                cdr3Hash += Objects.hashCode(al.getFeature(GeneFeature.CDR3));
        }

        try (FilePreCloneReader reader = new FilePreCloneReader(Paths.get(files.get(1)))) {
            long totalClones = reader.getNumberOfClones();

            // Checking and exporting alignments

            long numberOfAlignmentsCheck = 0;
            try (PrintStream ps = new PrintStream(files.get(2))) {
                ps.print("alignmentId\t");
                for (TagInfo ti : reader.getTagsInfo())
                    ps.print(ti.getName() + "\t");
                ps.println("readIndex\tcloneId\tcdr3\tbestV\tbestJ");
                for (VDJCAlignments al : CUtils.it(reader.readAlignments())) {
                    cdr3Hash -= Objects.hashCode(al.getFeature(GeneFeature.CDR3));
                    numberOfAlignmentsCheck++;
                    TObjectDoubleIterator<TagTuple> it = al.getTagCount().iterator();
                    while (it.hasNext()) {
                        it.advance();
                        ps.print(numberOfAlignmentsCheck + "\t");
                        for (TagValue tv : it.key())
                            ps.print(tv.toString() + "\t");
                        ps.print(al.getMinReadId() + "\t" +
                                al.getCloneIndex() + "\t");
                        if (al.getFeature(GeneFeature.CDR3) != null)
                            ps.print(al.getFeature(GeneFeature.CDR3).getSequence());
                        ps.print("\t");
                        if (al.getBestHit(GeneType.Variable) != null)
                            ps.print(al.getBestHit(GeneType.Variable).getGene().getName());
                        ps.print("\t");
                        if (al.getBestHit(GeneType.Joining) != null)
                            ps.print(al.getBestHit(GeneType.Joining).getGene().getName());
                        ps.println();
                    }
                }
            }

            if (cdr3Hash != 0)
                throwExecutionException("inconsistent alignment composition between initial file and pre-clone container");

            if (numberOfAlignmentsCheck != totalAlignments) {
                throwExecutionException("numberOfAlignmentsCheck != totalAlignments (" +
                        numberOfAlignmentsCheck + " != " + totalAlignments + ")");
            }

            for (VDJCAlignments al : CUtils.it(reader.readAssignedAlignments()))
                numberOfAlignmentsCheck--;

            for (VDJCAlignments al : CUtils.it(reader.readUnassignedAlignments()))
                numberOfAlignmentsCheck--;

            if (numberOfAlignmentsCheck != 0)
                throwExecutionException("numberOfAlignmentsCheck != 0 (" +
                        numberOfAlignmentsCheck + " != 0)");

            // Checking and exporting clones

            long numberOfClonesCheck = 0;
            try (PrintStream ps = new PrintStream(files.get(3))) {
                ps.print("cloneId\t");
                for (TagInfo ti : reader.getTagsInfo())
                    ps.print(ti.getName() + "\t");
                ps.println("count\tcdr3\tbestV\tbestJ");
                for (PreClone c : CUtils.it(reader.readPreClones())) {
                    TObjectDoubleIterator<TagTuple> it = c.getCoreTagCount().iterator();
                    while (it.hasNext()) {
                        it.advance();
                        ps.print(c.getIndex() + "\t");
                        for (TagValue tv : it.key())
                            ps.print(tv.toString() + "\t");
                        ps.print(it.value() + "\t");
                        ps.println(c.getClonalSequence()[0].getSequence() + "\t" +
                                c.getBestGene(GeneType.Variable).getName() + "\t" +
                                c.getBestGene(GeneType.Joining).getName());
                    }
                    numberOfClonesCheck++;
                }
            }

            if (numberOfClonesCheck != totalClones)
                throwExecutionException("numberOfClonesCheck != totalClones (" +
                        numberOfClonesCheck + " != " + totalClones + ")");
        }
    }
}
