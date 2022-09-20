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
package com.milaboratory.mixcr.util;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Chunk;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReaderCloseable;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mitool.data.CriticalThresholdCollection;
import com.milaboratory.mixcr.MiXCRParamsSpec;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters;
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.cli.AlignerReport;
import com.milaboratory.mixcr.cli.AlignerReportBuilder;
import com.milaboratory.mixcr.cli.CloneAssemblerReport;
import com.milaboratory.mixcr.cli.CloneAssemblerReportBuilder;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;
import static com.milaboratory.util.TempFileManager.getTempFile;
import static com.milaboratory.util.TempFileManager.systemTempFolderDestination;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class RunMiXCR {

    public static AssembleResult assemble(final AlignResult align) {
        return assemble(align, true);
    }

    public static AssembleResult assemble(final AlignResult align, boolean close) {
        CloneAssembler assembler = null;
        try {
            RunMiXCRAnalysis parameters = align.parameters;
            assembler = new CloneAssembler(parameters.cloneAssemblerParameters,
                    false, align.usedGenes, align.parameters.alignerParameters);

            CloneAssemblerReportBuilder report = new CloneAssemblerReportBuilder()
                    .setCommandLine("from test")
                    .setStartMillis(System.currentTimeMillis())
                    .setInputFiles()
                    .setOutputFiles();
            assembler.setListener(report);
            report.setTotalReads(align.totalNumberOfReads);

            AlignmentsProvider aProvider = new AlignmentsProvider() {
                @Override
                public OutputPortWithProgress<VDJCAlignments> readAlignments() {
                    return OutputPortWithProgress.wrap(align.alignments.size(), CUtils.asOutputPort(align.alignments));
                }

                @Override
                public long getNumberOfReads() {
                    return align.alignments.size();
                }

                @Override
                public void close() {
                }
            };

            PreCloneReader preClones = PreCloneReader.fromAlignments(
                    aProvider,
                    parameters.cloneAssemblerParameters.getAssemblingFeatures(),
                    report);
            CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(preClones, assembler);

            //start progress reporting
            SmartProgressReporter.startProgressReport(assemblerRunner);

            assemblerRunner.run();

            CloneSet cloneSet = assemblerRunner.getCloneSet(new MiXCRHeader(
                    new MiXCRParamsSpec("default_4.0"),
                    align.tagsInfo != null ? align.tagsInfo : TagsInfo.NO_TAGS,
                    align.parameters.alignerParameters,
                    align.parameters.cloneAssemblerParameters,
                    null,
                    null
            ), new MiXCRFooter(Collections.emptyList(), new CriticalThresholdCollection()));
            report.setFinishMillis(System.currentTimeMillis());
            return new AssembleResult(align, cloneSet, report.buildReport(), assembler);
        } finally {
            if (close)
                assembler.close();
        }
    }

    public static FullSeqAssembleResult assembleContigs(final AssembleResult assemble) {
        AlignResult align = assemble.alignResult;

        int totalClonesCount = 0;
        File tmpFile = getTempFile();
        try (ClnAReader reader = assemble.resultReader();
             PrimitivO tmpOut = new PrimitivO(new BufferedOutputStream(new FileOutputStream(tmpFile)));) {

            IOUtil.registerGeneReferences(tmpOut, align.usedGenes, align.parameters.alignerParameters);

            final CloneFactory cloneFactory = new CloneFactory(reader.getAssemblerParameters().getCloneFactoryParameters(),
                    reader.getAssemblingFeatures(), reader.getUsedGenes(), reader.getAlignerParameters().getFeaturesToAlignMap());

            ClnAReader.CloneAlignmentsPort cloneAlignmentsPort = reader.clonesAndAlignments();
            SmartProgressReporter.startProgressReport("Assembling", cloneAlignmentsPort);

            OutputPort<Clone[]> parallelProcessor = new ParallelProcessor<>(cloneAlignmentsPort, cloneAlignments -> {
                try {
                    FullSeqAssembler fullSeqAssembler = new FullSeqAssembler(cloneFactory,
                            align.parameters.fullSeqAssemblerParameters, cloneAlignments.clone,
                            align.parameters.alignerParameters);
                    FullSeqAssembler.RawVariantsData rawVariantsData =
                            fullSeqAssembler.calculateRawData(cloneAlignments::alignments);

                    return fullSeqAssembler.callVariants(rawVariantsData);
                } catch (Throwable re) {
                    throw new RuntimeException("While processing clone #" + cloneAlignments.clone.getId(), re);
                }
            }, 1); // TODO why ony one ???


            for (Clone[] clones : CUtils.it(parallelProcessor)) {
                totalClonesCount += clones.length;
                for (Clone cl : clones)
                    tmpOut.writeObject(cl);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Clone[] clones = new Clone[totalClonesCount];
        try (PrimitivI tmpIn = new PrimitivI(new BufferedInputStream(new FileInputStream(tmpFile)))) {
            IOUtil.registerGeneReferences(tmpIn, align.usedGenes, align.parameters.alignerParameters);
            int i = 0;
            for (Clone clone : CUtils.it(new PipeDataInputReader<>(Clone.class, tmpIn, totalClonesCount)))
                clones[i++] = clone;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        CloneSet cloneSet = new CloneSet(
                Arrays.asList(clones),
                align.usedGenes,
                new MiXCRHeader(
                        new MiXCRParamsSpec("default_4.0"),
                        align.tagsInfo != null ? align.tagsInfo : TagsInfo.NO_TAGS,
                        align.parameters.alignerParameters,
                        align.parameters.cloneAssemblerParameters,
                        null,
                        null
                ),
                new MiXCRFooter(Collections.emptyList(), new CriticalThresholdCollection()),
                VDJCSProperties.CO_BY_COUNT
        );

        return new FullSeqAssembleResult(assemble, cloneSet);
    }

    public static AlignResult align(String... files) throws Exception {
        return align(new RunMiXCRAnalysis(files));
    }

    public static AlignResult align(RunMiXCRAnalysis parameters) throws Exception {
        VDJCAlignerParameters alignerParameters = parameters.alignerParameters;

        VDJCAligner aligner = VDJCAligner.createAligner(alignerParameters,
                parameters.isInputPaired(), alignerParameters.getMergerParameters() != null);

        List<VDJCGene> genes = new ArrayList<>();
        for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary(parameters.library, parameters.species).getGenes(parameters.chains))
            if (alignerParameters.containsRequiredFeature(gene) &&
                    (gene.isFunctional() || !parameters.isFunctionalOnly)) {
                genes.add(gene);
                aligner.addGene(gene);
            }

        AlignerReportBuilder reportBuilder = new AlignerReportBuilder()
                .setCommandLine("from test")
                .setStartMillis(System.currentTimeMillis())
                .setInputFiles()
                .setOutputFiles();
        aligner.setEventsListener(reportBuilder);

        try (SequenceReaderCloseable<? extends SequenceRead> reader = parameters.getReader()) {

            //start progress reporting
            if (reader instanceof CanReportProgress)
                SmartProgressReporter.startProgressReport("align", (CanReportProgress) reader);

            OutputPort<Chunk<SequenceRead>> mainInputReads = CUtils.buffered((OutputPort) chunked(reader, 64), 16);
            OutputPort<VDJCAlignmentResult> alignments = unchunked(new ParallelProcessor(mainInputReads, chunked(aligner), parameters.threads));
            List<VDJCAlignments> als = new ArrayList<>();
            int ind = 0;
            for (VDJCAlignmentResult t : CUtils.it(new OrderedOutputPort<>(alignments, r -> r.read.getId()))) {
                if (t.alignment != null) {
                    t.alignment.setAlignmentsIndex(ind++);
                    als.add(t.alignment);
                }
            }
            reportBuilder.setFinishMillis(System.currentTimeMillis());
            return new AlignResult(parameters, reader.getNumberOfReads(), reportBuilder.buildReport(), als, genes, null, aligner);
        }
    }

    public static final class FullSeqAssembleResult {
        public final AssembleResult assembleResult;
        public final CloneSet cloneSet;

        public FullSeqAssembleResult(AssembleResult assembleResult, CloneSet cloneSet) {
            this.assembleResult = assembleResult;
            this.cloneSet = cloneSet;
        }
    }

    public static final class AssembleResult {
        public final AlignResult alignResult;
        public final CloneSet cloneSet;
        public final CloneAssemblerReport report;
        public final CloneAssembler cloneAssembler;

        public AssembleResult(AlignResult alignResult, CloneSet cloneSet, CloneAssemblerReport report, CloneAssembler cloneAssembler) {
            this.alignResult = alignResult;
            this.cloneSet = cloneSet;
            this.report = report;
            this.cloneAssembler = cloneAssembler;
        }

        private File clnaFile = null;

        public ClnAReader resultReader() throws IOException {
            if (clnaFile == null) {
                clnaFile = getTempFile();
                try (ClnAWriter writer = new ClnAWriter(clnaFile, systemTempFolderDestination("runmixcr.assembleContigs"))) {
                    writer.writeClones(cloneSet);
                    PreCloneReader preCloneReader = alignResult.asPreCloneReader();
                    // Pre-soring alignments
                    try (AlignmentsMappingMerger merged = new AlignmentsMappingMerger(
                            preCloneReader.readAlignments(),
                            cloneAssembler.getAssembledReadsPort())) {
                        writer.collateAlignments(merged, cloneAssembler.getAlignmentsCount());
                    }
                    writer.setFooter(new MiXCRFooter(Arrays.asList(alignResult.report, report),
                            new CriticalThresholdCollection()));
                    writer.writeAlignmentsAndIndex();
                }
            }
            return new ClnAReader(clnaFile.toPath(), VDJCLibraryRegistry.getDefault(), 1);
        }
    }

    public static final class AlignResult {
        public final RunMiXCRAnalysis parameters;
        public final long totalNumberOfReads;
        public final AlignerReport report;
        public final List<VDJCAlignments> alignments;
        public final List<VDJCGene> usedGenes;
        public final TagsInfo tagsInfo;
        public final VDJCAligner aligner;

        public AlignResult(RunMiXCRAnalysis parameters, long totalNumberOfReads, AlignerReport report,
                           List<VDJCAlignments> alignments, List<VDJCGene> usedGenes, TagsInfo tagsInfo, VDJCAligner aligner) {
            this.parameters = parameters;
            this.totalNumberOfReads = totalNumberOfReads;
            this.report = report;
            this.alignments = alignments;
            this.usedGenes = usedGenes;
            this.tagsInfo = tagsInfo;
            this.aligner = aligner;
        }

        private File alignmentsFile = null;

        public VDJCAlignmentsReader resultReader() throws IOException {
            if (alignmentsFile == null) {
                alignmentsFile = getTempFile();
                try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(alignmentsFile)) {
                    MiXCRHeader info = new MiXCRHeader(
                            new MiXCRParamsSpec("default_4.0"),
                            tagsInfo != null ? tagsInfo : TagsInfo.NO_TAGS,
                            aligner.getParameters(),
                            null,
                            null,
                            null
                    );
                    writer.writeHeader(info, usedGenes);
                    for (VDJCAlignments alignment : alignments)
                        writer.write(alignment);
                    writer.setNumberOfProcessedReads(totalNumberOfReads);
                    writer.setFooter(new MiXCRFooter(Arrays.asList(report), new CriticalThresholdCollection()));
                }
            }
            return new VDJCAlignmentsReader(alignmentsFile);
        }

        public Path alignmentsPath() {
            try {
                resultReader();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return alignmentsFile.toPath();
        }

        public PreCloneReader asPreCloneReader() throws IOException {
            return PreCloneReader.fromAlignments(
                    resultReader(),
                    parameters.cloneAssemblerParameters.getAssemblingFeatures(),
                    __ -> {
                    });
        }
    }

    public static final class RunMiXCRAnalysis {
        public VDJCAlignerParameters alignerParameters = VDJCParametersPresets.getByName("default");
        public CloneAssemblerParameters cloneAssemblerParameters = CloneAssemblerParametersPresets.getByName("default");
        public FullSeqAssemblerParameters fullSeqAssemblerParameters = FullSeqAssemblerParameters.getPresets().getByName("default");
        public String library = "default";
        public Chains chains = Chains.ALL;
        public String species = "hs";
        public boolean isFunctionalOnly = false;
        public int threads = Runtime.getRuntime().availableProcessors();
        public final SequenceReaderCloseable<? extends SequenceRead> reader;
        public final boolean isInputPaired;

        public RunMiXCRAnalysis(String... inputFiles) throws IOException {
            this.isInputPaired = inputFiles.length == 2;
            if (isInputPaired())
                reader = new PairedFastqReader(inputFiles[0], inputFiles[1], true);
            else {
                String[] s = inputFiles[0].split("\\.");
                if (s[s.length - 1].equals("fasta"))
                    reader = new FastaSequenceReaderWrapper(
                            new FastaReader<>(inputFiles[0], NucleotideSequence.ALPHABET),
                            true);
                else
                    reader = new SingleFastqReader(inputFiles[0], true);
            }
            cloneAssemblerParameters.updateFrom(alignerParameters);
        }

        public RunMiXCRAnalysis(SequenceReaderCloseable<? extends SequenceRead> reader, boolean isInputPaired) {
            this.reader = reader;
            this.isInputPaired = isInputPaired;
            cloneAssemblerParameters.updateFrom(alignerParameters);
        }

        public RunMiXCRAnalysis(final SequenceRead... input) {
            this.reader = new SequenceReaderCloseable<SequenceRead>() {
                int counter = 0;

                @Override
                public void close() {
                }

                @Override
                public long getNumberOfReads() {
                    return counter;
                }

                @Override
                public synchronized SequenceRead take() {
                    if (counter == input.length)
                        return null;
                    return input[counter++];
                }
            };
            this.isInputPaired = input.length > 0 && input[0] instanceof PairedRead;
            cloneAssemblerParameters.updateFrom(alignerParameters);
        }

        public boolean isInputPaired() {
            return isInputPaired;
        }

        public SequenceReaderCloseable<? extends SequenceRead> getReader() throws IOException {
            return reader;
        }
    }

    private static <T> OutputPortCloseable<T> opCloseable(final OutputPort<T> op) {
        return new OutputPortCloseable<T>() {
            @Override
            public void close() {
            }

            @Override
            public T take() {
                return op.take();
            }
        };
    }
}
