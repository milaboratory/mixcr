package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
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
import com.milaboratory.util.TempFileManager;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.hash.TIntLongHashMap;
import io.repseq.core.GeneFeature;
import kotlin.jvm.functions.Function1;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.List;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Parameters;

@Command(name = "assemblePreClones",
        separator = " ",
        hidden = true)
public class CommandAssemblePreClones extends ACommandMiXCR {
    @Parameters(arity = "2", description = "input_file output_file")
    public List<String> files;

    @CommandLine.Option(description = "Use system temp folder for temporary files, the output folder will be used if this option is omitted.",
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

        try (
                VDJCAlignmentsReader reader1 = new VDJCAlignmentsReader(files.get(0));
                VDJCAlignmentsReader reader2 = new VDJCAlignmentsReader(files.get(0));
                // For export
                VDJCAlignmentsReader reader3 = new VDJCAlignmentsReader(files.get(0));
                PreCloneWriter writer = new PreCloneWriter(Paths.get(files.get(1)),
                        TempFileManager.smartTempDestination(files.get(1), "", useSystemTemp))
                // //
                // PrintStream out = new PrintStream(new BufferedOutputStream(
                //         Files.newOutputStream(Paths.get(files.get(1))), 1 << 20))
        ) {
            writer.init(reader1);

            TagsInfo tagsInfo = reader1.getTagsInfo();
            int depth = tagsInfo.getDepthFor(TagType.CellTag);
            // int depth = tagsInfo.getDepthFor(TagType.MoleculeTag);
            if (tagsInfo.getSortingLevel() < depth)
                throwValidationException("Input file has insufficient sorting level");

            PreCloneAssemblerParameters params = new PreCloneAssemblerParameters(
                    gAssemblerParams, reader1.getParameters(),
                    new GeneFeature[]{GeneFeature.CDR3},
                    depth);
            PreCloneAssembler assembler = new PreCloneAssembler(params,
                    CUtils.wrap(reader1, VDJCAlignments::ensureKeyTags),
                    CUtils.wrap(reader2, VDJCAlignments::ensureKeyTags)
            );

            Function1<VDJCAlignments, TagTuple> gFunction = a -> a.getTagCount().asKeyPrefixOrError(depth);
            OutputPort<GroupOP<VDJCAlignments, TagTuple>> alGroups = PipeKt.group(CUtils.wrap(reader3,
                    VDJCAlignments::ensureKeyTags), gFunction);

            // TODO array?
            TIntLongHashMap alToCloneMapping = new TIntLongHashMap();
            List<PreCloneWithAlignments> clones;
            while ((clones = assembler.getForNextGroup()) != null) {
                GroupOP<VDJCAlignments, TagTuple> grp = alGroups.take();
                assert clones.isEmpty() || clones.get(0).preClone.coreKey.equals(grp.getKey());

                alToCloneMapping.clear();
                for (PreCloneWithAlignments cloneWA : clones) {
                    PreClone clone = cloneWA.preClone;
                    TIntIterator it = cloneWA.alignments.iterator();
                    while (it.hasNext())
                        alToCloneMapping.put(it.next(), clone.id);

                    writer.putClone(clone);
                    //
                    // out.println(
                    //         clone.clonalSequence[0].getSequence().toString() + "\t" +
                    //                 cloneWA.alignments.size() + "\t" +
                    //                 clone.geneScores.get(GeneType.Variable) + "\t" +
                    //                 clone.geneScores.get(GeneType.Joining) + "\t" +
                    //                 clone.geneScores.get(GeneType.Constant) + "\t" +
                    //                 clone.coreTagCount + "\t" +
                    //                 clone.fullTagCount + "\t" +
                    //                 rIds);
                }

                int localId = 0;
                for (VDJCAlignments al : CUtils.it(grp))
                    writer.putAlignment(al.withCloneIndex(alToCloneMapping.get(localId++)));
            }

            writer.finishWrite();

            assembler.getReport().writeReport(ReportHelper.STDOUT);
        }
    }
}
