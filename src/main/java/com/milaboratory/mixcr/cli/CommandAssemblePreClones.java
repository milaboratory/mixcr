package com.milaboratory.mixcr.cli;

import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.mitool.consensus.AAssemblerParameters;
import com.milaboratory.mitool.consensus.GConsensusAssemblerParameters;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.tags.PreClone;
import com.milaboratory.mixcr.tags.PreCloneAssembler;
import com.milaboratory.mixcr.tags.PreCloneAssemblerParameters;
import com.milaboratory.util.ReportHelper;
import io.repseq.core.GeneFeature;

import java.util.List;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Parameters;

@Command(name = "assemblePreClones",
        separator = " ",
        hidden = true)
public class CommandAssemblePreClones extends ACommandMiXCR {
    @Parameters(description = "input_file")
    public String inputFile;

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

        try (
                VDJCAlignmentsReader reader1 = new VDJCAlignmentsReader(inputFile);
                VDJCAlignmentsReader reader2 = new VDJCAlignmentsReader(inputFile)
        ) {
            TagsInfo tagsInfo = reader1.getTagsInfo();
            int depth = tagsInfo.getDepthFor(TagType.CellTag);
            if (tagsInfo.getSortingLevel() < depth)
                throwValidationException("Input file has insufficient sorting level");

            PreCloneAssemblerParameters params = new PreCloneAssemblerParameters(
                    gAssemblerParams, reader1.getParameters(),
                    new GeneFeature[]{GeneFeature.CDR3},
                    depth);
            PreCloneAssembler assembler = new PreCloneAssembler(params, reader1, reader2);
            List<PreClone> clones;
            while ((clones = assembler.getForNextGroup()) != null) {
            }
            assembler.getReport().writeReport(ReportHelper.STDOUT);
        }
    }
}
