package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.mitool.consensus.AAssemblerParameters;
import com.milaboratory.mitool.consensus.GConsensusAssemblerParameters;
import com.milaboratory.mitool.helpers.GroupOP;
import com.milaboratory.mitool.helpers.PipeKt;
import com.milaboratory.mixcr.assembler.preclone.*;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.GeneFeature;
import kotlin.jvm.functions.Function1;

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
            .scoring(LinearGapAlignmentScoring.getNucleotideBLASTScoring())
            .minAlignmentScore(40)
            .maxAlignmentPenalty(90)
            .trimMinimalSumQuality(20)
            .trimReferenceRegion(false)
            .maxQuality((byte) 45)
            .build();
    private static final GConsensusAssemblerParameters gAssemblerParams = GConsensusAssemblerParameters.builder()
            .aAssemblerParameters(aAssemblerParams)
            .maxIterations(4)
            .minAltSeedQualityScore((byte) 11)
            .minimalRecordShare(0.001)
            .minimalRecordCount(2)
            .build();

    @Override
    public void run0() throws Exception {
        long totalAlignments;
        long totalClones = 0;
        try (
                VDJCAlignmentsReader reader1 = new VDJCAlignmentsReader(files.get(0));
                VDJCAlignmentsReader reader2 = new VDJCAlignmentsReader(files.get(0));
                // For export
                VDJCAlignmentsReader reader3 = new VDJCAlignmentsReader(files.get(0));
                PreCloneWriter writer = new PreCloneWriter(Paths.get(files.get(1)),
                        TempFileManager.smartTempDestination(files.get(1), "", useSystemTemp))
        ) {
            writer.init(reader1);

            TagsInfo tagsInfo = reader1.getTagsInfo();
            int depth = tagsInfo.getDepthFor(cellLevel ? TagType.CellTag : TagType.MoleculeTag);
            // int depth = tagsInfo.getDepthFor(TagType.MoleculeTag);
            if (tagsInfo.getSortingLevel() < depth)
                throwValidationException("Input file has insufficient sorting level");

            PreCloneAssemblerParameters params = new PreCloneAssemblerParameters(
                    gAssemblerParams, reader1.getParameters(),
                    new GeneFeature[]{GeneFeature.CDR3},
                    depth);
            CountingOutputPort<VDJCAlignments> reader1c = new CountingOutputPort<>(reader1);
            PreCloneAssembler assembler = new PreCloneAssembler(params,
                    CUtils.wrap(reader1c, VDJCAlignments::ensureKeyTags),
                    CUtils.wrap(reader2, VDJCAlignments::ensureKeyTags)
            );

            Function1<VDJCAlignments, TagTuple> gFunction = a -> a.getTagCount().asKeyPrefixOrError(depth);
            OutputPort<GroupOP<VDJCAlignments, TagTuple>> alGroups = PipeKt.group(
                    CUtils.wrap(reader3, VDJCAlignments::ensureKeyTags), gFunction);

            SmartProgressReporter.startProgressReport("Building pre-clones", reader1);

            PreCloneAssemblerResult result;
            while ((result = assembler.getForNextGroup()) != null) {
                GroupOP<VDJCAlignments, TagTuple> grp = alGroups.take();
                List<PreClone> clones = result.getClones();
                assert clones.isEmpty() || clones.get(0).coreKey.equals(grp.getKey());
                totalClones += clones.size();

                for (PreClone clone : clones)
                    writer.putClone(clone);

                int localId = 0;
                for (VDJCAlignments al : CUtils.it(grp)) {
                    long cloneMapping = result.getCloneForAlignment(localId++);
                    writer.putAlignment(cloneMapping != -1
                            ? al.withCloneIndexAndMappingType(cloneMapping, (byte) 0)
                            : al);
                }
            }

            totalAlignments = reader1c.getCount();

            writer.finish();

            assembler.getReport().writeReport(ReportHelper.STDOUT);
        }

        try (PreCloneReader reader = new PreCloneReader(Paths.get(files.get(1)))) {
            long numberOfAlignmentsCheck = 0;
            for (VDJCAlignments al : CUtils.it(reader.readAllAlignments()))
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
            for (PreClone c : CUtils.it(reader.readClones()))
                numberOfClonesCheck++;

            if (numberOfClonesCheck != totalClones)
                throwExecutionException("numberOfClonesCheck != totalClones (" +
                        numberOfClonesCheck + " != " + totalClones + ")");
        }
    }
}
