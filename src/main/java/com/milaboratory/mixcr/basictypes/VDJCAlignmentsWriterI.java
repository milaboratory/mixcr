/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
