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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationWriter;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.VDJCGene;

import java.util.List;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public interface VDJCAlignmentsWriterI extends PipelineConfigurationWriter, AutoCloseable {
    void setNumberOfProcessedReads(long numberOfProcessedReads);

    void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, PipelineConfiguration pipelineConfiguration, TagsInfo tagsInfo);

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
        public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, PipelineConfiguration pipelineConfiguration, TagsInfo tagsInfo) {
        }

        @Override
        public void write(VDJCAlignments alignment) {
        }

        @Override
        public void close() {
        }
    }
}
