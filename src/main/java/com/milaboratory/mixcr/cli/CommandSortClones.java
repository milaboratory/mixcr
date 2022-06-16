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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.util.ArraysUtils;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;

import static com.milaboratory.mixcr.basictypes.IOUtil.MAGIC_CLNA;
import static com.milaboratory.mixcr.basictypes.IOUtil.MAGIC_CLNS;
import static com.milaboratory.mixcr.cli.CommandSortClones.SORT_CLONES_COMMAND_NAME;
import static com.milaboratory.util.TempFileManager.smartTempDestination;


@CommandLine.Command(name = SORT_CLONES_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Sort clones by sequence. Clones in the output file will be sorted by clonal sequence, which allows to build overlaps between clonesets.")
public class CommandSortClones extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String SORT_CLONES_COMMAND_NAME = "sortClones";

    @CommandLine.Option(description = "Use system temp folder for temporary files, the output folder will be used if this option is omitted.",
            names = {"--use-system-temp"})
    public boolean useSystemTemp = false;

    @Override
    public ActionConfiguration<SortConfiguration> getConfiguration() {
        return new SortConfiguration();
    }

    @Override
    public void run1() throws Exception {
        switch (Objects.requireNonNull(IOUtil.fileInfoExtractorInstance.getFileInfo(new File(in))).fileType) {
            case MAGIC_CLNS:
                try (ClnsReader reader = new ClnsReader(Paths.get(in), VDJCLibraryRegistry.getDefault());
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
                try (ClnAReader reader = new ClnAReader(Paths.get(in), VDJCLibraryRegistry.getDefault(), Runtime.getRuntime().availableProcessors());
                     ClnAWriter writer = new ClnAWriter(getFullPipelineConfiguration(), out, smartTempDestination(out, "", useSystemTemp))) {
                    SmartProgressReporter.startProgressReport(writer);

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

                    writer.writeClones(CloneSet.reorder(reader.readCloneSet(), ordering));
                    writer.collateAlignments(reader.readAllAlignments(), reader.numberOfAlignments());
                    writer.writeAlignmentsAndIndex();
                }
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
