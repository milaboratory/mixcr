/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ActionMergeAlignments extends AbstractActionWithResumeOption {
    final MergeAlignmentsParameters parameters = new MergeAlignmentsParameters();

    @Override
    public void go0(ActionHelper helper) throws Exception {
        try (MultiReader reader = new MultiReader(parameters.getInputFiles());
             VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(parameters.getOutputFileName())) {
            reader.initNextReader();
            SmartProgressReporter.startProgressReport("Merging", reader);
            writer.header(reader.currentInnerReader.getParameters(), reader.currentInnerReader.getUsedGenes(), parameters.getFullPipelineConfiguration());
            for (VDJCAlignments record : CUtils.it(reader))
                writer.write(record);
            writer.setNumberOfProcessedReads(reader.readIdOffset.get());
        }
    }

    @Override
    public String command() {
        return "mergeAlignments";
    }

    @Override
    public MergeAlignmentsParameters params() {
        return parameters;
    }

    public static class MergeConfiguration implements ActionConfiguration {
        final PipelineConfiguration[] sources;

        @JsonCreator
        public MergeConfiguration(@JsonProperty("sources") PipelineConfiguration[] sources) {
            this.sources = sources;
        }

        @Override
        public String actionName() {
            return "merge";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MergeConfiguration that = (MergeConfiguration) o;
            return Arrays.equals(sources, that.sources);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(sources);
        }
    }

    @Parameters(commandDescription = "Merge several *.vdjca[.gz] files with alignments into a single alignments file.")
    public static final class MergeAlignmentsParameters extends ActionParametersWithResumeOption {
        @Parameter(description = "[input_file1.vdjca[.gz] [input_file2.vdjca[.gz] ....]] output_file.vdjca[.gz]")
        public List<String> parameters;

        public String getOutputFileName() {
            return parameters.get(parameters.size() - 1);
        }

        @Override
        public List<String> getInputFiles() {
            return parameters.subList(0, parameters.size() - 1);
        }

        private MergeConfiguration configuration = null;

        @Override
        public ActionConfiguration getConfiguration() {
            return configuration != null
                    ? configuration
                    : (configuration = new MergeConfiguration(getInputFiles().stream().map(PipelineConfigurationReader::fromFile).toArray(PipelineConfiguration[]::new)));
        }

        @Override
        public PipelineConfiguration getFullPipelineConfiguration() {
            return PipelineConfiguration.mkInitial(getInputFiles(), getConfiguration());
        }

        @Override
        protected List<String> getOutputFiles() {
            return parameters.subList(parameters.size() - 1, parameters.size());
        }
    }

    // Not thread-safe !
    private static final class MultiReader implements
                                           OutputPort<VDJCAlignments>, CanReportProgress, AutoCloseable {
        final VDJCLibraryRegistry registry = VDJCLibraryRegistry.getDefault();
        final List<String> files;
        final AtomicInteger fileId = new AtomicInteger(0);
        final AtomicLong recordId = new AtomicLong(), readIdOffset = new AtomicLong();
        volatile double progress = 0.0;
        VDJCAlignmentsReader currentInnerReader;

        public MultiReader(List<String> files) {
            this.files = files;
        }

        private void updateProgress() {
            double p = 1.0 * (fileId.get() - 1) / files.size();
            if (currentInnerReader != null)
                p += currentInnerReader.getProgress() / files.size();
            this.progress = p;
        }

        @Override
        public double getProgress() {
            return progress;
        }

        @Override
        public boolean isFinished() {
            return fileId.get() > files.size();
        }

        public boolean initNextReader() {
            try {
                if (currentInnerReader == null) {
                    int idToCreate = fileId.getAndIncrement();
                    if (idToCreate >= files.size())
                        return false;
                    currentInnerReader = new VDJCAlignmentsReader(files.get(idToCreate), registry);
                }
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public VDJCAlignments take() {
            VDJCAlignments record;
            if (currentInnerReader == null || (record = currentInnerReader.take()) == null) {
                if (currentInnerReader != null) {
                    readIdOffset.addAndGet(currentInnerReader.getNumberOfReads());
                    currentInnerReader.close();
                    currentInnerReader = null;
                }
                if (!initNextReader())
                    return null;
                return take();
            } else {
                updateProgress();
                return record.shiftReadId(recordId.incrementAndGet(), readIdOffset.get());
            }
        }

        @Override
        public void close() throws Exception {
            if (currentInnerReader != null)
                currentInnerReader.close();
        }
    }
}
