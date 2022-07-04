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
package com.milaboratory.mixcr.assembler.preclone;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.mitool.helpers.GroupOP;
import com.milaboratory.mitool.helpers.PipeKt;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.ProgressAndStage;
import com.milaboratory.util.TempFileDest;
import io.repseq.core.GeneFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class PreCloneAssemblerRunner implements CanReportProgressAndStage, AutoCloseable {
    private final ProgressAndStage ps = new ProgressAndStage("Initialization");

    private final VDJCAlignmentsReader alignments;
    private final GeneFeature[] assemblingFeatures;
    private final TagsInfo outputTagsInfo;

    // It takes three passes over the alignments file from scratch to final file with pre-clones
    private final VDJCAlignmentsReader.SecondaryReader reader1, reader2, reader3;
    private final PreCloneAssembler assembler;
    private final Path outputFile;
    private final TempFileDest tempDest;

    public PreCloneAssemblerRunner(VDJCAlignmentsReader alignments,
                                   TagType groupingLevel,
                                   GeneFeature[] assemblingFeatures,
                                   PreCloneAssemblerParameters parameters,
                                   Path outputFile,
                                   TempFileDest tempDest) {
        this.alignments = alignments;

        TagsInfo tagsInfo = alignments.getTagsInfo();
        int depth = tagsInfo.getDepthFor(groupingLevel);
        if (tagsInfo.getSortingLevel() < depth)
            throw new IllegalArgumentException("Input file has insufficient sorting level");

        this.assemblingFeatures = assemblingFeatures;
        this.outputTagsInfo = tagsInfo.setSorted(depth);

        // PreCloneAssemblerParameters assemblerParams = new PreCloneAssemblerParameters(
        //         gAssemblerParams, alignments.getParameters(),
        //         assemblingFeatures, depth);

        this.reader1 = alignments.readAlignments();
        this.reader2 = alignments.readAlignments();
        this.reader3 = alignments.readAlignments();

        // Progress of the first step can either be tracked by reader1 or reader2
        ps.delegate("Building pre-clones from tag groups", reader1);

        this.assembler = new PreCloneAssembler(parameters,
                assemblingFeatures, depth,
                CUtils.wrap(reader1, VDJCAlignments::ensureKeyTags),
                CUtils.wrap(reader2, VDJCAlignments::ensureKeyTags),
                alignments.getParameters());

        this.outputFile = outputFile;
        this.tempDest = tempDest;
    }

    public PreCloneAssemblerReportBuilder getReport() {
        return assembler.getReport();
    }

    public void run() throws IOException {
        try (FilePreCloneWriter writer = new FilePreCloneWriter(outputFile, tempDest)) {
            OutputPort<GroupOP<VDJCAlignments, TagTuple>> alGroups = PipeKt.group(
                    CUtils.wrap(reader3, VDJCAlignments::ensureKeyTags), assembler.getGroupingFunction());
            writer.init(alignments, assemblingFeatures, outputTagsInfo);

            PreCloneAssemblerResult result;
            while ((result = assembler.getForNextGroup()) != null) {
                GroupOP<VDJCAlignments, TagTuple> grp = alGroups.take();
                List<PreCloneImpl> clones = result.getClones();
                assert clones.isEmpty() || clones.get(0).getCoreKey().equals(grp.getKey());

                for (PreCloneImpl clone : clones)
                    writer.putClone(clone);

                int localId = 0;
                for (VDJCAlignments al : CUtils.it(grp)) {
                    long cloneMapping = result.getCloneForAlignment(localId++);
                    writer.putAlignment(cloneMapping != -1
                            ? al.withCloneIndexAndMappingType(cloneMapping, (byte) 0)
                            : al);
                }
            }

            ps.delegate("Writing pre-clones", writer);

            writer.finish();
        } finally {
            ps.unDelegate();
            ps.finish();
        }
    }

    /** Creates a reader to access the result of this operation */
    public FilePreCloneReader createReader() throws IOException {
        return new FilePreCloneReader(outputFile);
    }

    @Override
    public double getProgress() {
        return ps.getProgress();
    }

    @Override
    public boolean isFinished() {
        return ps.isFinished();
    }

    @Override
    public String getStage() {
        return ps.getStage();
    }

    @Override
    public void close() throws Exception {
        reader1.close();
        reader2.close();
        reader3.close();
    }
}
