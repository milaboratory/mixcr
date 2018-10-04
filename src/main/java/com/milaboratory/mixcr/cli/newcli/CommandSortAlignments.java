package com.milaboratory.mixcr.cli.newcli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.mixcr.basictypes.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.ObjectSerializer;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.Sorter;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.VDJCGene;
import picocli.CommandLine.Command;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.milaboratory.mixcr.cli.newcli.CommandSortAlignments.SORT_ALIGNMENTS_COMMAND_NAME;

@Command(name = SORT_ALIGNMENTS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Sort alignments in vdjca file.")
public class CommandSortAlignments extends ACommandWithResumeWithSingleInput {
    static final String SORT_ALIGNMENTS_COMMAND_NAME = "sortAlignments";

    @Override
    public ActionConfiguration getConfiguration() {
        return new SortConfiguration();
    }

    @Override
    public void run1() throws Exception {
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in)) {
            SmartProgressReporter.startProgressReport("Reading vdjca", reader);
            try (OutputPortCloseable<VDJCAlignments> sorted =
                         Sorter.sort(reader, idComparator, 1024 * 512, new VDJCAlignmentsSerializer(reader), TempFileManager.getTempFile());
                 VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {

                writer.header(reader.getParameters(), reader.getUsedGenes(), getFullPipelineConfiguration());

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
            try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(stream)) {
                writer.header(parameters, usedAlleles, null);
                for (VDJCAlignments datum : data)
                    writer.write(datum);
            }
        }

        @Override
        public OutputPort<VDJCAlignments> read(InputStream stream) {
            VDJCAlignmentsReader reader = new VDJCAlignmentsReader(stream);
            VDJCAlignmentsReader.initGeneFeatureReferencesFrom(reader, parameters);
            return reader;
        }
    }

    public static class SortConfiguration implements ActionConfiguration {
        @Override
        public String actionName() {
            return "sortAlignments";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(SortConfiguration.class);
        }
    }
}
