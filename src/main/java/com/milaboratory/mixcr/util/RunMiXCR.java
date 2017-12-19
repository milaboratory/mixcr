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
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.cli.AlignerReport;
import com.milaboratory.mixcr.cli.CloneAssemblerReport;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;

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

            CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(new AlignmentsProvider() {
                @Override
                public OutputPortCloseable<VDJCAlignments> create() {
                    return opCloseable(CUtils.asOutputPort(align.alignments));
                }

                @Override
                public long getTotalNumberOfReads() {
                    return align.alignments.size();
                }
            }, assembler, parameters.threads);

            //start progress reporting
            SmartProgressReporter.startProgressReport(assemblerRunner);

            assemblerRunner.run();

            CloneSet cloneSet = assemblerRunner.getCloneSet();
            return new AssembleResult(cloneSet, report, assembler);
        } finally {
            if (close)
                assembler.close();
        }
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
            for (VDJCAlignmentResult t : CUtils.it(new OrderedOutputPort<>(alignments, new Indexer<VDJCAlignmentResult>() {
                @Override
                public long getIndex(VDJCAlignmentResult r) {
                    return r.read.getId();
                }
            }))) {
                if (t.alignment != null) {
                    t.alignment.setAlignmentsIndex(ind++);
                    als.add(t.alignment);
                }
            }
            return new AlignResult(parameters, reader.getNumberOfReads(), report, als, genes, aligner);
        }
    }

    public static final class AssembleResult {
        public final CloneSet cloneSet;
        public final CloneAssemblerReport report;
        public final CloneAssembler cloneAssembler;

        public AssembleResult(CloneSet cloneSet, CloneAssemblerReport report, CloneAssembler cloneAssembler) {
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
        public final VDJCAligner aligner;

        public AlignResult(RunMiXCRAnalysis parameters, long totalNumberOfReads, AlignerReport report,
                           List<VDJCAlignments> alignments, List<VDJCGene> usedGenes, VDJCAligner aligner) {
            this.parameters = parameters;
            this.totalNumberOfReads = totalNumberOfReads;
            this.report = report;
            this.alignments = alignments;
            this.usedGenes = usedGenes;
            this.aligner = aligner;
        }

        private byte[] serializedAlignments = null;

        public VDJCAlignmentsReader resultReader() {
            if (serializedAlignments == null) {
                final ByteArrayOutputStream data = new ByteArrayOutputStream();
                try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(data)) {
                    writer.header(aligner);
                    for (VDJCAlignments alignment : alignments)
                        writer.write(alignment);
                    writer.setNumberOfProcessedReads(totalNumberOfReads);
                }
                serializedAlignments = data.toByteArray();
            }
            return new VDJCAlignmentsReader(new ByteArrayInputStream(serializedAlignments));
        }
    }

    public static final class RunMiXCRAnalysis {
        public VDJCAlignerParameters alignerParameters = VDJCParametersPresets.getByName("default");
        public CloneAssemblerParameters cloneAssemblerParameters = CloneAssemblerParametersPresets.getByName("default");
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
        }

        public RunMiXCRAnalysis(SequenceReaderCloseable<? extends SequenceRead> reader, boolean isInputPaired) {
            this.reader = reader;
            this.isInputPaired = isInputPaired;
        }

        public RunMiXCRAnalysis(final SequenceRead... input) {
            this.reader = new SequenceReaderCloseable<SequenceRead>() {
                int counter = 0;

                @Override
                public void close() {
                }

                @Override
                public long getNumberOfReads() {
                    return input.length;
                }

                @Override
                public synchronized SequenceRead take() {
                    if (counter == input.length)
                        return null;
                    return input[counter++];
                }
            };
            this.isInputPaired = input.length > 0 && input[0] instanceof PairedRead;
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
