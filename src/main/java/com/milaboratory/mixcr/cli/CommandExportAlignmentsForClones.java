package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.*;

/**
 *
 */
@Command(name = "exportAlignmentsForClones",
        sortOptions = true,
        separator = " ",
        description = "Export alignments for particular clones from \"clones & alignments\" (*.clna) file.")
public class CommandExportAlignmentsForClones extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String EXPORT_ALIGNMENTS_FOR_CLONES_COMMAND_NAME = "exportAlignmentsForClones";

    // @Override
    // @Parameters(index = "0", description = "input_file.clna")
    // public void setIn(String in) {
    //     super.setIn(in);
    // }
    //
    // @Override
    // @Parameters(index = "1", description = "[output_file.vdjca[.gz]")
    // public void setOut(String out) {
    //     super.setOut(out);
    // }

    @Option(names = "--id", description = "[cloneId1 [cloneId2 [cloneId3]]]", arity = "0..*")
    public List<Integer> ids = new ArrayList<>();

    //    @CommandLine.Option(description = "Create separate files for each clone. File with '_clnN' suffix, " +
    //            "where N is clone index, will be created for each clone index.",
    //            names = {"-s", "--separate"})
    //    public boolean separate = false;

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
             VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(getOutput())) {
            writer.header(clna.getAlignerParameters(), clna.getGenes(), getFullPipelineConfiguration());

            long count = 0;
            if (getCloneIds().length == 0)
                for (VDJCAlignments al : CUtils.it(clna.readAssembledAlignments())) {
                    writer.write(al);
                    ++count;
                }
            else
                for (int id : getCloneIds()) {
                    OutputPortCloseable<VDJCAlignments> reader = clna.readAlignmentsOfClone(id);
                    VDJCAlignments al;
                    while ((al = reader.take()) != null) {
                        writer.write(al);
                        ++count;
                    }
                }

            writer.setNumberOfProcessedReads(count);
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
    public static class ExportAlignmentsConfiguration implements ActionConfiguration {
        public final Set<Integer> cloneIds;

        @JsonCreator
        public ExportAlignmentsConfiguration(@JsonProperty("cloneIds") Set<Integer> cloneIds) {
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