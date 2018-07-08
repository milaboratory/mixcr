package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.VDJCGene;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public interface VDJCAlignmentsWriterI extends AnalysisHistoryWriter, AutoCloseable {
    void setNumberOfProcessedReads(long numberOfProcessedReads);

    void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, AnalysisHistory history);

    void write(VDJCAlignments alignment);

    @Override
    void close();

    final class DummyWriter implements VDJCAlignmentsWriterI {
        public static final DummyWriter INSTANCE = new DummyWriter();

        private DummyWriter() {
        }

        @Override
        public void setNumberOfProcessedReads(long numberOfProcessedReads) {
        }

        @Override
        public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, AnalysisHistory history) {
        }

        @Override
        public void write(VDJCAlignments alignment) {
        }

        @Override
        public void close() {
        }

        @Override
        public void writeHistory(AnalysisHistory history) {
        }
    }

    final class ArrayWriter implements VDJCAlignmentsWriterI {
        public long numberOfProcessedReads;
        public VDJCAlignerParameters parameters;
        public List<VDJCGene> genes;
        public AnalysisHistory history;
        public final ArrayList<VDJCAlignments> data;

        public ArrayWriter(int capacity) {
            data = new ArrayList<>(capacity);
        }

        public ArrayWriter() {
            this(10);
        }

        @Override
        public void setNumberOfProcessedReads(long numberOfProcessedReads) {
            this.numberOfProcessedReads = numberOfProcessedReads;
        }

        @Override
        public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, AnalysisHistory history) {
            this.parameters = parameters;
            this.genes = genes;
            this.history = history;
        }

        @Override
        public synchronized void write(VDJCAlignments alignment) {
            data.add(alignment);
        }

        @Override
        public void writeHistory(AnalysisHistory history) {
            this.history = history;
        }

        @Override
        public void close() {}
    }
}
