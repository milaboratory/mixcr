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
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.TempFileDest;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.GeneFeature;

import java.nio.file.Paths;
import java.util.List;

import static picocli.CommandLine.*;

@Command(name = "itestAssemblePreClones",
        separator = " ",
        hidden = true)
public class ITestCommandAssemblePreClones extends ACommandMiXCR {
    @Parameters(arity = "2", description = "input_file output_file")
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
        try (VDJCAlignmentsReader alignmentsReader = new VDJCAlignmentsReader(files.get(0))) {
            totalAlignments = alignmentsReader.getNumberOfAlignments();
            PreCloneAssemblerRunner assemblerRunner = new PreCloneAssemblerRunner(
                    alignmentsReader,
                    cellLevel ? TagType.CellTag : TagType.MoleculeTag,
                    new GeneFeature[]{GeneFeature.CDR3},
                    PreCloneAssemblerParameters.DefaultGConsensusAssemblerParameters,
                    Paths.get(files.get(1)),
                    tmp);
            SmartProgressReporter.startProgressReport(assemblerRunner);
            assemblerRunner.run();
            assemblerRunner.getReport().writeReport(ReportHelper.STDOUT);
        }

        try (FilePreCloneReader reader = new FilePreCloneReader(Paths.get(files.get(1)))) {
            long totalClones = reader.getNumberOfClones();

            long numberOfAlignmentsCheck = 0;
            for (VDJCAlignments al : CUtils.it(reader.readAlignments()))
                numberOfAlignmentsCheck++;

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

            long numberOfClonesCheck = 0;
            for (PreClone c : CUtils.it(reader.readPreClones()))
                numberOfClonesCheck++;

            if (numberOfClonesCheck != totalClones)
                throwExecutionException("numberOfClonesCheck != totalClones (" +
                        numberOfClonesCheck + " != " + totalClones + ")");
        }
    }
}
