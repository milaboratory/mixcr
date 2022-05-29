package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.mitool.consensus.AAssemblerParameters;
import com.milaboratory.mitool.consensus.GConsensusAssemblerParameters;
import com.milaboratory.mitool.helpers.GroupOP;
import com.milaboratory.mitool.helpers.PipeKt;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssembler;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters;
import com.milaboratory.util.ReportHelper;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TIntLongMap;
import gnu.trove.map.hash.TIntLongHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import kotlin.jvm.functions.Function1;

import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
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
                PrintStream out = new PrintStream(new BufferedOutputStream(
                        Files.newOutputStream(Paths.get(files.get(1))), 1 << 20))
        ) {
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

            List<PreClone> clones;
            while ((clones = assembler.getForNextGroup()) != null) {
                GroupOP<VDJCAlignments, TagTuple> grp = alGroups.take();
                assert clones.isEmpty() || clones.get(0).coreKey.equals(grp.getKey());
                TIntLongMap localToMitReadIndex = new TIntLongHashMap();
                int localId = 0;
                for (VDJCAlignments al : CUtils.it(grp))
                    localToMitReadIndex.put(localId++, al.getMinReadId());

                for (PreClone clone : clones) {

                    TLongArrayList rIds = new TLongArrayList();
                    TIntIterator it = clone.alignments.iterator();
                    while (it.hasNext())
                        rIds.add(localToMitReadIndex.get(it.next()));

                    out.println(
                            clone.clonalSequence[0].getSequence().toString() + "\t" +
                                    clone.alignments.size() + "\t" +
                                    clone.geneScores.get(GeneType.Variable) + "\t" +
                                    clone.geneScores.get(GeneType.Joining) + "\t" +
                                    clone.geneScores.get(GeneType.Constant) + "\t" +
                                    clone.coreTagCount + "\t" +
                                    clone.fullTagCount + "\t" +
                                    rIds);
                }
            }
            assembler.getReport().writeReport(ReportHelper.STDOUT);
        }
    }
}
