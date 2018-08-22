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
        try(VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFile())) {
            SmartProgressReporter.startProgressReport("Reading vdjca", reader);
            try(OutputPortCloseable<VDJCAlignments> sorted =
                        Sorter.sort(reader, idComparator, 1024 * 512, new VDJCAlignmnetsSerializer(reader), TempFileManager.getTempFile());
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
            return Long.compare(o1.getReadId(), o2.getReadId());
        }
    };

    private static final class VDJCAlignmnetsSerializer implements ObjectSerializer<VDJCAlignments> {
        final VDJCAlignerParameters parameters;
        final List<VDJCGene> usedAlleles;

        public VDJCAlignmnetsSerializer(VDJCAlignmentsReader reader) {
            this.parameters = reader.getParameters();
            this.usedAlleles = reader.getUsedGenes();
        }

        @Override
        public void write(Collection<VDJCAlignments> data, OutputStream stream) {
            try(VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(stream)) {
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
