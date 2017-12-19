package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.CountingOutputPort;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.ObjectSerializer;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.Sorter;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.VDJCGene;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Created by poslavsky on 28/02/2017.
 */
public final class ActionSortAlignments implements Action {
    final AParameters parameters = new AParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFile())) {
            SmartProgressReporter.startProgressReport("Reading vdjca", reader);
            try (OutputPortCloseable<VDJCAlignments> sorted =
                         Sorter.sort(reader, idComparator, 1024 * 512, new VDJCAlignmentsSerializer(reader), TempFileManager.getTempFile());
                 VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(parameters.getOutputFile())) {

                writer.header(reader.getParameters(), reader.getUsedGenes());

                final long nReads = reader.getNumberOfReads();
                final CountingOutputPort<VDJCAlignments> counter = new CountingOutputPort<>(sorted);
                SmartProgressReporter.startProgressReport("Writing sorted alignments", SmartProgressReporter.extractProgress(counter, nReads));
                for (VDJCAlignments res : CUtils.it(counter))
                    writer.write(res);
                writer.setNumberOfProcessedReads(nReads);
            }
        }
    }

    @Override
    public String command() {
        return "sortAlignments";
    }

    @Override
    public ActionParameters params() {
        return parameters;
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
                writer.header(parameters, usedAlleles);
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

    @Parameters(commandDescription = "Sort alignments in vdjca file")
    private static final class AParameters extends ActionParametersWithOutput {
        @Parameter(description = "input.vdjca output.vdjca")
        public List<String> parameters;

        public String getInputFile() {
            return parameters.get(0);
        }


        public String getOutputFile() {
            return parameters.get(1);
        }

        @Override
        protected List<String> getOutputFiles() {
            return Arrays.asList(getOutputFile());
        }

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }
    }
}
