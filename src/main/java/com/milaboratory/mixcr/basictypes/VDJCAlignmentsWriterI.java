/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationWriter;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.VDJCGene;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public interface VDJCAlignmentsWriterI extends PipelineConfigurationWriter, AutoCloseable {
    void setNumberOfProcessedReads(long numberOfProcessedReads);

    void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, PipelineConfiguration pipelineConfiguration);

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
        public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, PipelineConfiguration pipelineConfiguration) {
        }

        @Override
        public void write(VDJCAlignments alignment) {
        }

        @Override
        public void close() {
        }
    }

    final class ArrayWriter implements VDJCAlignmentsWriterI {
        public long numberOfProcessedReads;
        public VDJCAlignerParameters parameters;
        public List<VDJCGene> genes;
        public PipelineConfiguration pipelineConfiguration;
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
        public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, PipelineConfiguration pipelineConfiguration) {
            this.parameters = parameters;
            this.genes = genes;
            this.pipelineConfiguration = pipelineConfiguration;
        }

        @Override
        public synchronized void write(VDJCAlignments alignment) {
            data.add(alignment);
        }

        @Override
        public void close() {}
    }
}
