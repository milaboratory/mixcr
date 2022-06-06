/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.util;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Chunk;
import cc.redberry.pipe.util.Indexer;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReaderCloseable;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters;
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.cli.AlignerReport;
import com.milaboratory.mixcr.cli.CloneAssemblerReport;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.Chains;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;
import static com.milaboratory.util.TempFileManager.*;

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

            CloneAssemblerReport report = new CloneAssemblerReport();
            assembler.setListener(report);

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

            PreCloneReader preClones = PreCloneReader.fromAlignments(aProvider,
                    parameters.cloneAssemblerParameters.getAssemblingFeatures());
            CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(preClones, assembler);

            //start progress reporting
            SmartProgressReporter.startProgressReport(assemblerRunner);

            assemblerRunner.run();

            CloneSet cloneSet = assemblerRunner.getCloneSet(align.parameters.alignerParameters, align.tagsInfo);
            return new AssembleResult(align, cloneSet, report, assembler);
        } finally {
            if (close)
                assembler.close();
        }
    }

    public static FullSeqAssembleResult assembleContigs(final AssembleResult assemble) {
        AlignResult align = assemble.alignResult;

        File clnaFile = getTempFile();
        try (ClnAWriter writer = new ClnAWriter(null, clnaFile, systemTempFolderDestination("runmixcr.assembleContigs"))) {
            // writer will supply current stage and completion percent to the progress reporter
            SmartProgressReporter.startProgressReport(writer);
            // Writing clone block

            writer.writeClones(assemble.cloneSet);
            // Pre-soring alignments
            try (AlignmentsMappingMerger merged = new AlignmentsMappingMerger(
                    CUtils.asOutputPort(align.alignments),
                    assemble.cloneAssembler.getAssembledReadsPort())) {
                writer.collateAlignments(merged, assemble.cloneAssembler.getAlignmentsCount());
            }
            writer.writeAlignmentsAndIndex();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int totalClonesCount = 0;
        File tmpFile = getTempFile();
        try (ClnAReader reader = new ClnAReader(clnaFile.toPath(), VDJCLibraryRegistry.getDefault(), 2);  // TODO concurrency ???
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

        CloneSet cloneSet = new CloneSet(Arrays.asList(clones), align.usedGenes, align.parameters.alignerParameters,
                align.parameters.cloneAssemblerParameters, align.tagsInfo, VDJCSProperties.CO_BY_COUNT);

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

        AlignerReport report = new AlignerReport();
        aligner.setEventsListener(report);

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
            return new AlignResult(parameters, reader.getNumberOfReads(), report, als, genes, null, aligner);
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
                    writer.header(aligner, null, tagsInfo);
                    for (VDJCAlignments alignment : alignments)
                        writer.write(alignment);
                    writer.setNumberOfProcessedReads(totalNumberOfReads);
                }
            }
            return new VDJCAlignmentsReader(alignmentsFile);
        }
    }

    public static final class RunMiXCRAnalysis {
        public VDJCAlignerParameters alignerParameters = VDJCParametersPresets.getByName("default");
        public CloneAssemblerParameters cloneAssemblerParameters = CloneAssemblerParametersPresets.getByName("default");
        public FullSeqAssemblerParameters fullSeqAssemblerParameters = FullSeqAssemblerParameters.getByName("default");
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
