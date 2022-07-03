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
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeReader;
import com.milaboratory.primitivio.PipeWriter;
import com.milaboratory.util.ObjectSerializer;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.sorting.Sorter;
import io.repseq.core.VDJCGene;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.milaboratory.mixcr.cli.CommandSortAlignments.SORT_ALIGNMENTS_COMMAND_NAME;

@Command(name = SORT_ALIGNMENTS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Sort alignments in vdjca file by read id.")
public class CommandSortAlignments extends MiXCRCommand {
    static final String SORT_ALIGNMENTS_COMMAND_NAME = "sortAlignments";

    @Parameters(description = "alignments.vdjca", index = "0")
    public String in;

    @Parameters(description = "alignments.sorted.vdjca", index = "1")
    public String out;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    @Override
    public void run0() throws Exception {
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in)) {
            SmartProgressReporter.startProgressReport("Reading vdjca", reader);
            try (OutputPortCloseable<VDJCAlignments> sorted =
                         Sorter.sort(reader, idComparator, 1024 * 512,
                                 new VDJCAlignmentsSerializer(reader), TempFileManager.getTempFile());
                 VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {

                writer.header(reader.getParameters(), reader.getUsedGenes(), reader.getTagsInfo());

                final long nReads = reader.getNumberOfReads();
                final CountingOutputPort<VDJCAlignments> counter = new CountingOutputPort<>(sorted);
                SmartProgressReporter.startProgressReport("Writing sorted alignments", SmartProgressReporter.extractProgress(counter, nReads));
                for (VDJCAlignments res : CUtils.it(counter))
                    writer.write(res);
                writer.setNumberOfProcessedReads(nReads);
            }
        }
    }

    private static final Comparator<VDJCAlignments> idComparator = new Comparator<VDJCAlignments>() {
        @Override
        public int compare(VDJCAlignments o1, VDJCAlignments o2) {
            return Long.compare(o1.getMinReadId(), o2.getMinReadId());
        }
    };

    public static final class VDJCAlignmentsSerializer implements ObjectSerializer<VDJCAlignments> {
        final VDJCAlignerParameters parameters;
        final List<VDJCGene> usedAlleles;

        public VDJCAlignmentsSerializer(VDJCAlignmentsReader reader) {
            this.parameters = reader.getParameters();
            this.usedAlleles = reader.getUsedGenes();
        }

        @Override
        public void write(Collection<VDJCAlignments> data, OutputStream stream) {
            try (PipeWriter<VDJCAlignments> out =
                         new PipeWriter<VDJCAlignments>(stream) {
                             @Override
                             protected void init() {
                                 IOUtil.stdVDJCPrimitivOStateInit(output, usedAlleles, parameters);
                             }
                         }) {
                for (VDJCAlignments datum : data)
                    out.put(datum);
            }
        }

        @Override
        public OutputPort<VDJCAlignments> read(InputStream stream) {
            return new PipeReader<VDJCAlignments>(VDJCAlignments.class, stream) {
                @Override
                protected void init() {
                    IOUtil.stdVDJCPrimitivIStateInit(input, parameters,
                            usedAlleles.get(0).getParentLibrary().getParent());
                }
            };
        }
    }
}
