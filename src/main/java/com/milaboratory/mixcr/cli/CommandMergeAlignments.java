/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.fasterxml.jackson.annotation.*;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.cli.CommandMergeAlignments.MERGE_ALIGNMENTS_COMMAND_NAME;

@Command(name = MERGE_ALIGNMENTS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Merge several *.vdjca files with alignments into a single alignments file.")
public class CommandMergeAlignments extends ACommandWithSmartOverwriteMiXCR {
    static final String MERGE_ALIGNMENTS_COMMAND_NAME = "mergeAlignments";

    @Parameters(description = "[input_file1.vdjca [input_file2.vdjca ....]] output_file.vdjca", arity = "2..*")
    public List<String> input;

    @Override
    public List<String> getInputFiles() {
        return input.subList(0, input.size() - 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        return input.subList(input.size() - 1, input.size());
    }

    private MergeConfiguration configuration = null;

    @Override
    public ActionConfiguration getConfiguration() {
        return configuration != null
                ? configuration
                : (configuration = new MergeConfiguration(getInputFiles().stream()
                .map(PipelineConfigurationReaderMiXCR::sFromFile).toArray(PipelineConfiguration[]::new)));
    }

    @Override
    public PipelineConfiguration getFullPipelineConfiguration() {
        return PipelineConfiguration.mkInitial(getInputFiles(), getConfiguration(),
                MiXCRVersionInfo.getAppVersionInfo());
    }

    @Override
    public void run1() throws Exception {
        try (MultiReader reader = new MultiReader(getInputFiles());
             VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(getOutput())) {
            reader.initNextReader();
            SmartProgressReporter.startProgressReport("Merging", reader);
            writer.header(reader.currentInnerReader.getParameters(), reader.currentInnerReader.getUsedGenes(), getFullPipelineConfiguration());
            for (VDJCAlignments record : CUtils.it(reader))
                writer.write(record);
            writer.setNumberOfProcessedReads(reader.readIdOffset.get());
        }
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    public static class MergeConfiguration implements ActionConfiguration {
        final PipelineConfiguration[] sources;

        @JsonCreator
        public MergeConfiguration(@JsonProperty("sources") PipelineConfiguration[] sources) {
            this.sources = sources;
        }

        @Override
        public String actionName() {
            return MERGE_ALIGNMENTS_COMMAND_NAME;
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
