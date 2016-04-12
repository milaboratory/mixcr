package com.milaboratory.mixcr.util;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Chunk;
import cc.redberry.pipe.util.Indexer;
import cc.redberry.pipe.util.OrderedOutputPort;
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
import com.milaboratory.mixcr.cli.ActionAlign;
import com.milaboratory.mixcr.cli.AlignerReport;
import com.milaboratory.mixcr.cli.CloneAssemblerReport;
import com.milaboratory.mixcr.reference.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class RunMiXCR {

    public static AssembleResult assemble(final AlignResult align) {
        RunMiXCRAnalysis parameters = align.parameters;
        try (CloneAssembler assembler = new CloneAssembler(parameters.cloneAssemblerParameters,
                false, align.usedAlleles)) {

            CloneAssemblerReport report = new CloneAssemblerReport();
            assembler.setListener(report);

            CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(new AlignmentsProvider() {
                @Override
                public OutputPortCloseable<VDJCAlignments> create() {
                    return opCloseable(CUtils.asOutputPort(align.alignments));
                }

                @Override
                public VDJCAlignerParameters getAlignerParameters() {
                    return align.parameters.alignerParameters;
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
            return new AssembleResult(cloneSet, report, assembler.getAssembledReadsPort());
        }
    }

    public static AlignResult align(String... files) throws Exception {
        return align(new RunMiXCRAnalysis(files));
    }

    public static AlignResult align(RunMiXCRAnalysis parameters) throws Exception {
        VDJCAlignerParameters alignerParameters = parameters.alignerParameters;

        VDJCAligner aligner = VDJCAligner.createAligner(alignerParameters,
                parameters.isInputPaired(), alignerParameters.getMergerParameters() != null);

        LociLibrary ll = LociLibraryManager.getDefault().getLibrary("mi");

        List<Allele> alleles = new ArrayList<>();
        for (Locus locus : parameters.loci)
            for (Allele allele : ll.getLocus(parameters.taxonId, locus).getAllAlleles())
                if (alignerParameters.containsRequiredFeature(allele) &&
                        (allele.isFunctional() || !parameters.isFunctionalOnly)) {
                    alleles.add(allele);
                    aligner.addAllele(allele);
                }

        AlignerReport report = new AlignerReport();
        aligner.setEventsListener(report);

        try (SequenceReaderCloseable<? extends SequenceRead> reader = parameters.createReader()) {

            //start progress reporting
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
                    t.alignment.setDescriptions(ActionAlign.extractDescription(t.read));
                    t.alignment.setOriginalSequences(ActionAlign.extractNSeqs(t.read));
                    als.add(t.alignment);
                }
            }
            return new AlignResult(parameters, reader.getNumberOfReads(), report, als, alleles, aligner);
        }
    }

    public static final class AssembleResult {
        final CloneSet cloneSet;
        final CloneAssemblerReport report;
        final OutputPortCloseable<ReadToCloneMapping> assembledReadsPort;

        public AssembleResult(CloneSet cloneSet, CloneAssemblerReport report, OutputPortCloseable<ReadToCloneMapping> assembledReadsPort) {
            this.cloneSet = cloneSet;
            this.report = report;
            this.assembledReadsPort = assembledReadsPort;
        }
    }

    public static final class AlignResult {
        final RunMiXCRAnalysis parameters;
        final long totalNumberOfReads;
        final AlignerReport report;
        final List<VDJCAlignments> alignments;
        final List<Allele> usedAlleles;
        final VDJCAligner aligner;

        public AlignResult(RunMiXCRAnalysis parameters, long totalNumberOfReads, AlignerReport report,
                           List<VDJCAlignments> alignments, List<Allele> usedAlleles, VDJCAligner aligner) {
            this.parameters = parameters;
            this.totalNumberOfReads = totalNumberOfReads;
            this.report = report;
            this.alignments = alignments;
            this.usedAlleles = usedAlleles;
            this.aligner = aligner;
        }
    }

    public static final class RunMiXCRAnalysis {
        public VDJCAlignerParameters alignerParameters = VDJCParametersPresets.getByName("default");
        public CloneAssemblerParameters cloneAssemblerParameters = CloneAssemblerParametersPresets.getByName("default");
        public Set<Locus> loci = EnumSet.allOf(Locus.class);
        public int taxonId = Species.HomoSapiens;
        public boolean isFunctionalOnly = true;
        public int threads = Runtime.getRuntime().availableProcessors();
        public final String[] inputFiles;

        public RunMiXCRAnalysis(String... inputFiles) {
            this.inputFiles = inputFiles;
        }

        public boolean isInputPaired() {
            return inputFiles.length == 2;
        }

        public SequenceReaderCloseable<? extends SequenceRead> createReader() throws IOException {
            if (isInputPaired())
                return new PairedFastqReader(inputFiles[0], inputFiles[1], true);
            else {
                String[] s = inputFiles[0].split("\\.");
                if (s[s.length - 1].equals("fasta"))
                    return new FastaSequenceReaderWrapper(
                            new FastaReader<>(inputFiles[0], NucleotideSequence.ALPHABET),
                            true);
                else
                    return new SingleFastqReader(inputFiles[0], true);
            }
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
