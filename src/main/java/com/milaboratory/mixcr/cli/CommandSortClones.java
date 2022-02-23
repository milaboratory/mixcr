package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.util.ArraysUtils;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

import static com.milaboratory.mixcr.basictypes.IOUtil.MAGIC_CLNA;
import static com.milaboratory.mixcr.basictypes.IOUtil.MAGIC_CLNS;
import static com.milaboratory.mixcr.cli.CommandSortClones.SORT_CLONES_COMMAND_NAME;


@CommandLine.Command(name = SORT_CLONES_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Sort clones by sequence. Clones in the output file will be sorted by clonal sequence, which allows to build overlaps between clonesets.")
public class CommandSortClones extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String SORT_CLONES_COMMAND_NAME = "sortClones";

    @Override
    public ActionConfiguration<SortConfiguration> getConfiguration() {
        return new SortConfiguration();
    }

    @Override
    public void run1() throws Exception {
        switch (Objects.requireNonNull(IOUtil.fileInfoExtractorInstance.getFileInfo(new File(in))).fileType) {
            case MAGIC_CLNS:
                try (ClnsReader reader = new ClnsReader(Path.of(in), VDJCLibraryRegistry.getDefault());
                     ClnsWriter writer = new ClnsWriter(out)) {

                    GeneFeature[] assemblingFeatures = reader.getAssemblerParameters().getAssemblingFeatures();

                    // Any CDR3 containing feature will become first
                    for (int i = 0; i < assemblingFeatures.length; i++)
                        if (assemblingFeatures[i].contains(GeneFeature.CDR3)) {
                            if (i != 0)
                                ArraysUtils.swap(assemblingFeatures, 0, i);
                            break;
                        }

                    VDJCSProperties.CloneOrdering ordering = VDJCSProperties.cloneOrderingByNucleotide(assemblingFeatures,
                            GeneType.Variable, GeneType.Joining);

                    writer.writeCloneSet(reader.getPipelineConfiguration(), CloneSet.reorder(reader.getCloneSet(), ordering));
                }
                return;

            case MAGIC_CLNA:
                throw new RuntimeException();

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
    public static class SortConfiguration implements ActionConfiguration<SortConfiguration> {
        @Override
        public String actionName() {
            return "sortClones";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(CommandSortAlignments.SortConfiguration.class);
        }
    }
}
