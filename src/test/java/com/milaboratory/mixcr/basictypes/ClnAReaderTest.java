/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.mitool.tag.TagsInfo;
import com.milaboratory.mixcr.assembler.AlignmentsMappingMerger;
import com.milaboratory.mixcr.assembler.CloneAssemblerParametersPresets;
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader;
import com.milaboratory.mixcr.presets.MiXCRParamsSpec;
import com.milaboratory.mixcr.presets.MiXCRStepParams;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.mixcr.util.RunMiXCR;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.tests.MiXCRTestUtils.emptyFooter;
import static com.milaboratory.util.TempFileManager.smartTempDestination;
import static org.junit.Assert.assertEquals;

public class ClnAReaderTest {
    @Test
    public void test1() throws Exception {
        testGeneric(clones -> {
                    Collections.shuffle(clones);
            clones = clones.stream().filter(c -> c.getId() != 2).collect(Collectors.toList());
                    return clones;
                }, als ->
                        CUtils.wrap(als, vdjcAlignments ->
                                vdjcAlignments.getCloneIndex() == 2
                                        ? vdjcAlignments.withMapping(new ReadToCloneMapping(vdjcAlignments.getAlignmentsIndex(), -1, false, false, false, false))
                                        : vdjcAlignments)
        );
    }

    @Test
    public void test3NoM1() throws Exception {
        testGeneric(clones -> clones,
                als -> new FilteringPort<>(als, a -> a.getCloneIndex() >= 0));
    }

    public void testGeneric(Function<List<Clone>, List<Clone>> modifyClones,
                            Function<OutputPort<VDJCAlignments>, OutputPort<VDJCAlignments>> modifyAlignments) throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        params.cloneAssemblerParameters.setAddReadsCountOnClustering(true);
        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align, false);

        PreCloneReader preCloneReader = align.asPreCloneReader();
        AlignmentsMappingMerger merged = new AlignmentsMappingMerger(preCloneReader.readAlignments(),
                assemble.cloneAssembler.getAssembledReadsPort());

        File file = TempFileManager.newTempFile();
        ClnAWriter writer = new ClnAWriter(file, smartTempDestination(file, "", false));

        List<Clone> newClones = assemble.cloneSet.getClones().stream()
                .map(Clone::withTotalCountsReset)
                .collect(Collectors.toList());
        CloneSet newCloneSet = new CloneSet.Builder(
                modifyClones.apply(newClones), align.usedGenes,
                new MiXCRHeader("hashA123", new MiXCRParamsSpec("legacy-4.0-default"), new MiXCRStepParams(), TagsInfo.NO_TAGS,
                        align.parameters.alignerParameters,
                        align.parameters.alignerParameters.getFeaturesToAlignMap(),
                        CloneAssemblerParametersPresets.getByName("default"),
                        null, null, false, null)
        )
                .sort(VDJCSProperties.CO_BY_COUNT)
                .calculateTotalCounts()
                .recalculateRanks()
                .build();
        writer.writeClones(newCloneSet);

        OutputPort<VDJCAlignments> als = modifyAlignments.apply(merged);
        CountingOutputPort<VDJCAlignments> alsc = CountingOutputPort.wrap(als);
        writer.collateAlignments(alsc, align.alignments.size());
        writer.setFooter(emptyFooter());
        writer.writeAlignmentsAndIndex();
        writer.close();

        ClnAReader reader = new ClnAReader(file.toPath(), VDJCLibraryRegistry.getDefault(),
                ThreadLocalRandom.current().nextInt(1, 17));

        assertEquals(MiXCRVersionInfo.get().getVersionString(AppVersionInfo.OutputType.ToFile),
                reader.versionInfo);

        assertEquals(alsc.getCount(), reader.numberOfAlignments());
        assertEquals(newCloneSet.size(), reader.numberOfClones());

        for (ClnAReader.CloneAlignments c : CUtils.it(reader.clonesAndAlignments())) {
            assertEquals("cloneId = " + c.getCloneId(), c.getClone().getCount(), count(c.alignments()), 0.01);
            assertEquals(c.getCloneId(), c.getClone().getId());
            CUtils.it(c.alignments()).forEach(a -> {
                assertEquals(c.getCloneId(), a.getCloneIndex());
                if (a.getMappingType() == ReadToCloneMapping.MappingType.Core)
                    assertEquals(c.getClone().getFeature(GeneFeature.CDR3),
                            a.getFeature(GeneFeature.CDR3));
            });
        }
    }

    @Test
    public void test2Empty() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);

        File file = TempFileManager.newTempFile();
        ClnAWriter writer = new ClnAWriter(file, smartTempDestination(file, "", false));
        writer.writeClones(new CloneSet.Builder(
                        Collections.emptyList(),
                        align.usedGenes,
                        new MiXCRHeader(
                                null,
                                new MiXCRParamsSpec("legacy-4.0-default"),
                                new MiXCRStepParams(),
                                TagsInfo.NO_TAGS,
                                align.parameters.alignerParameters,
                                align.parameters.alignerParameters.getFeaturesToAlignMap(),
                                CloneAssemblerParametersPresets.getByName("default"),
                                null,
                                null,
                                false,
                                null
                        )
                )
                        .sort(VDJCSProperties.CO_BY_COUNT)
                        .calculateTotalCounts()
                        .recalculateRanks()
                        .build()
        );
        writer.collateAlignments(CUtils.asOutputPort(align.alignments), align.alignments.size());
        writer.setFooter(emptyFooter());
        writer.writeAlignmentsAndIndex();
        writer.close();

        ClnAReader reader = new ClnAReader(file.toPath(), VDJCLibraryRegistry.getDefault(), 17);

        assertEquals(MiXCRVersionInfo.get().getVersionString(AppVersionInfo.OutputType.ToFile),
                reader.versionInfo);

        assertEquals(align.alignments.size(), reader.numberOfAlignments());
        assertEquals(0, reader.numberOfClones());

        for (ClnAReader.CloneAlignments c : CUtils.it(reader.clonesAndAlignments())) {
            assertEquals("" + c.getCloneId(), c.getClone().getCount(), count(c.alignments()), 0.01);
            assertEquals(c.getCloneId(), c.getClone().getId());
            CUtils.it(c.alignments()).forEach(a -> {
                assertEquals(c.getCloneId(), a.getCloneIndex());
                if (a.getMappingType() == ReadToCloneMapping.MappingType.Core)
                    assertEquals(c.getClone().getFeature(GeneFeature.CDR3), a.getFeature(GeneFeature.CDR3));
            });
        }
    }

    static int count(OutputPort port) {
        int c = 0;
        while (port.take() != null)
            ++c;
        return c;
    }
}
