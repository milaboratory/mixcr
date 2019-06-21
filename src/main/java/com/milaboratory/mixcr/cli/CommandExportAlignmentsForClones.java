package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceWriter;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceWriterWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 *
 */
@CommandLine.Command(name = "exportAlignmentsForClones",
        sortOptions = true,
        separator = " ",
        description = "Export alignments for particular clones from \"clones & alignments\" (*.clna) file.")
public class CommandExportAlignmentsForClones extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String EXPORT_ALIGNMENTS_FOR_CLONES_COMMAND_NAME = "exportAlignmentsForClones";

    @CommandLine.Parameters(index = "0", description = "input_file.clna")
    public String in;

    @CommandLine.Parameters(index = "1", description = "[output_file.vdjca[.gz]")
    public String out;

    @CommandLine.Option(names = "--id", description = "[cloneId1 [cloneId2 [cloneId3]]]", arity = "0..*")
    public List<Integer> ids = new ArrayList<>();

    @CommandLine.Option(description = "Create separate files for each clone. File with '_clnN' suffix, " +
            "where N is clone index, will be created for each clone index.",
            names = {"-s", "--separate"})
    public boolean separate = false;

    @Override
    public ActionConfiguration getConfiguration() {
        return new ExportAlignmentsConfiguration(new HashSet<>(ids));
    }

    public int[] getCloneIds() {
        return ids.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    @Override
    public void run1() throws Exception {
        try (ClnAReader clna = new ClnAReader(in, VDJCLibraryRegistry.getDefault());
             VDJCAlignmentsWriter writer  = new VDJCAlignmentsWriter(getOutput())) {
            writer.header(clna.getAlignerParameters(), clna.getGenes(), getConfiguration());

            VDJCAlignments firstAlignment;
            try (OutputPortCloseable<VDJCAlignments> dummyP = clna.readAllAlignments()) {
                firstAlignment = dummyP.take();
            }

            if (firstAlignment == null)
                return;

            if (firstAlignment.getOriginalReads() == null)
                throwValidationException("Error: original reads were not saved in original .vdjca file: " +
                        "re-run 'align' with '-OsaveOriginalReads=true' option.");

            int[] cid = getCloneIds();
            Supplier<IntStream> cloneIds;
            if (cid.length == 0)
                cloneIds = () -> IntStream.range(0, clna.numberOfClones());
            else
                cloneIds = () -> IntStream.of(cid);

            long totalAlignments = cloneIds.get().mapToLong(clna::numberOfAlignmentsInClone).sum();
            AtomicLong alignmentsWritten = new AtomicLong();
            AtomicBoolean finished = new AtomicBoolean(false);

            SmartProgressReporter.startProgressReport("Writing reads", new CanReportProgress() {
                @Override
                public double getProgress() {
                    return 1.0 * alignmentsWritten.get() / totalAlignments;
                }

                @Override
                public boolean isFinished() {
                    return finished.get();
                }
            });

            boolean paired = firstAlignment.getOriginalReads().get(0).numberOfReads() == 2;

            try (SequenceWriter globalWriter = separate ? null : createWriter(paired, out)) {
                cloneIds.get().forEach(cloneId -> {
                    try (SequenceWriter individualWriter = globalWriter == null ? createWriter(paired, cloneFile(out, cloneId)) : null) {
                        SequenceWriter actualWriter = globalWriter == null ? individualWriter : globalWriter;
                        for (VDJCAlignments alignments : CUtils.it(clna.readAlignmentsOfClone(cloneId))) {
                            for (SequenceRead read : alignments.getOriginalReads())
                                actualWriter.write(read);
                            alignmentsWritten.incrementAndGet();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private static String cloneFile(String fileName, int id) {
        if (fileName.contains(".fast"))
            fileName = fileName.replace(".fast", "_cln" + id + ".fast");
        else fileName += id;
        return fileName;
    }



    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    public static class ExportAlignmentsConfiguration implements ActionConfiguration {
        public final Set<Integer> cloneIds;

        public ExportAlignmentsConfiguration(Set<Integer> cloneIds) {
            this.cloneIds = cloneIds;
        }

        @Override
        public String actionName() {
            return EXPORT_ALIGNMENTS_FOR_CLONES_COMMAND_NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExportAlignmentsConfiguration that = (ExportAlignmentsConfiguration) o;
            return cloneIds.equals(that.cloneIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cloneIds);
        }
    }
}