/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.StatusReporter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner;
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.*;
import gnu.trove.iterator.TObjectDoubleIterator;
import io.repseq.core.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.milaboratory.mixcr.cli.CommandAssemble.ASSEMBLE_COMMAND_NAME;
import static com.milaboratory.util.TempFileManager.smartTempDestination;

@Command(name = ASSEMBLE_COMMAND_NAME,
        separator = " ",
        description = "Assemble clones.")
public class CommandAssemble extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String ASSEMBLE_COMMAND_NAME = "assemble";

    @Option(description = "Clone assembling parameters preset.",
            names = {"-p", "--preset"})
    public String assemblerParametersName = "default";

    @Option(description = "Processing threads",
            names = {"-t", "--threads"})
    public void setThreads(int threads) {
        System.out.println("-t / --threads is deprecated for \"mixcr assemble ...\" and ignored for this call...");
    }

    @Option(description = "Use system temp folder for temporary files, the output folder will be used if this option is omitted.",
            names = {"--use-system-temp"})
    public boolean useSystemTemp = false;

    @Option(description = "Use higher compression for output file.",
            names = {"--high-compression"})
    public boolean highCompression = false;

    @Option(description = "Sort by sequence. Clones in the output file will be sorted by clonal sequence," +
            "which allows to build overlaps between clonesets.",
            names = {"-s", "--sort-by-sequence"})
    public boolean sortBySequence = false;

    @Option(description = CommonDescriptions.REPORT,
            names = {"-r", "--report"})
    public String reportFile;

    @Option(description = CommonDescriptions.JSON_REPORT,
            names = {"-j", "--json-report"})
    public String jsonReport;

    @Option(description = "Show buffer statistics.",
            names = {"--buffers"}, hidden = true)
    public boolean reportBuffers;

    @Option(description = "If this option is specified, output file will be written in \"Clones & " +
            "Alignments\" format (*.clna), containing clones and all corresponding alignments. " +
            "This file then can be used to build wider contigs for clonal sequence and extract original " +
            "reads for each clone (if -OsaveOriginalReads=true was use on 'align' stage).",
            names = {"-a", "--write-alignments"})
    public boolean isClnaOutput = false;

    @Option(description = "If tags are present, do assemble pre-clones on the cell level rather than on the molecule level. " +
            "If there are no molecular tags in the data, but cell tags are present, this option will be used by default. " +
            "This option has no effect on the data without tags.",
            names = {"--cell-level"})
    public boolean cellLevel = false;

    @Option(names = "-O", description = "Overrides default parameter values.")
    private Map<String, String> overrides = new HashMap<>();

    @Override
    public ActionConfiguration getConfiguration() {
        ensureParametersInitialized();
        return new AssembleConfiguration(getCloneAssemblerParameters(), isClnaOutput, ordering);
    }

    // Extracting V/D/J/C gene list from input vdjca file
    private List<VDJCGene> genes = null;
    private VDJCAlignerParameters alignerParameters = null;
    private TagsInfo tagsInfo = null;
    private CloneAssemblerParameters assemblerParameters = null;
    private VDJCSProperties.CloneOrdering ordering = null;

    private void ensureParametersInitialized() {
        if (assemblerParameters != null)
            return;

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in,
                VDJCLibraryRegistry.getDefault())) {
            genes = reader.getUsedGenes();
            // Saving aligner parameters to correct assembler parameters
            alignerParameters = reader.getParameters();
            tagsInfo = reader.getTagsInfo();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assert alignerParameters != null;

        //set aligner parameters
        assemblerParameters = CloneAssemblerParametersPresets.getByName(assemblerParametersName);
        if (assemblerParameters == null)
            throwValidationException("Unknown parameters: " + assemblerParametersName);
        // noinspection ConstantConditions
        assemblerParameters = assemblerParameters.updateFrom(alignerParameters);

        // TODO 4.0 ???
        // assemblerParameters = assemblerParameters.setCloneClusteringParameters(assemblerParameters.getCloneClusteringParameters().setMinimalTagSetOverlap(DEFAULT_MINIMAL_TAG_SET_OVERLAP));

        // Overriding JSON parameters
        if (!overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters, CloneAssemblerParameters.class,
                    overrides);
            if (assemblerParameters == null)
                throwValidationException("Failed to override some parameter: " + overrides);
        }

        if (sortBySequence) {
            GeneFeature[] assemblingFeatures = assemblerParameters.getAssemblingFeatures();

            // Any CDR3 containing feature will become first
            for (int i = 0; i < assemblingFeatures.length; i++)
                if (assemblingFeatures[i].contains(GeneFeature.CDR3)) {
                    if (i != 0)
                        ArraysUtils.swap(assemblingFeatures, 0, i);
                    break;
                }

            ordering = VDJCSProperties.cloneOrderingByNucleotide(assemblingFeatures,
                    GeneType.Variable, GeneType.Joining);
        } else {
            ordering = VDJCSProperties.CO_BY_COUNT;
        }
    }

    public CloneAssemblerParameters getCloneAssemblerParameters() {
        ensureParametersInitialized();
        return assemblerParameters;
    }

    public List<VDJCGene> getGenes() {
        ensureParametersInitialized();
        return genes;
    }

    public VDJCAlignerParameters getAlignerParameters() {
        ensureParametersInitialized();
        return alignerParameters;
    }

    public TagsInfo getTagsInfo() {
        ensureParametersInitialized();
        return tagsInfo;
    }

    public VDJCSProperties.CloneOrdering getOrdering() {
        ensureParametersInitialized();
        return ordering;
    }

    /**
     * Assemble report
     */
    public final CloneAssemblerReport report = new CloneAssemblerReport();

    @Override
    public void run1() throws Exception {
        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();

        // Will be used for several operations requiring disk offloading
        TempFileDest tempDest = smartTempDestination(out, "", useSystemTemp);

        // Checking consistency between actionParameters.doWriteClnA() value and file extension
        if ((getOutput().toLowerCase().endsWith(".clna") && !isClnaOutput) ||
                (getOutput().toLowerCase().endsWith(".clns") && isClnaOutput))
            warn("WARNING: Unexpected file extension, use .clns extension for clones-only (normal) output and\n" +
                    ".clna if -a / --write-alignments options specified.");

        try (VDJCAlignmentsReader alignmentsReader = new VDJCAlignmentsReader(in)) {

            CloneAssemblerParameters assemblerParameters = getCloneAssemblerParameters();
            List<VDJCGene> genes = getGenes();
            VDJCAlignerParameters alignerParameters = getAlignerParameters();
            TagsInfo tagsInfo = getTagsInfo();

            // tagsInfo.getSortingLevel()

            // Performing assembly
            try (CloneAssembler assembler = new CloneAssembler(assemblerParameters,
                    isClnaOutput,
                    genes, alignerParameters.getFeaturesToAlignMap())) {
                // Creating event listener to collect run statistics
                report.setStartMillis(beginTimestamp);
                report.setInputFiles(in);
                report.setOutputFiles(out);
                report.setCommandLine(getCommandLineArguments());

                assembler.setListener(report);

                // TODO >>>>>>>>>>>>>>
                PreCloneReader preClones;
                if (tagsInfo.hasTagsWithType(TagType.CellTag) || tagsInfo.hasTagsWithType(TagType.MoleculeTag)) {
                    int depth = tagsInfo.getDepthFor(cellLevel ? TagType.CellTag : TagType.MoleculeTag);
                    if (tagsInfo.getSortingLevel() < depth)
                        throwValidationException("Input file has insufficient sorting level");
                    PreCloneAssemblerParameters preAssemblerParams = new PreCloneAssemblerParameters(
                            PreCloneAssemblerParameters.DefaultGConsensusAssemblerParameters,
                            alignmentsReader.getParameters(),
                            assemblerParameters.getAssemblingFeatures(),
                            depth);
                    Path preClonesFile = tempDest.resolvePath("preclones.pc");

                    PreCloneAssemblerRunner assemblerRunner = new PreCloneAssemblerRunner(
                            alignmentsReader,
                            cellLevel ? TagType.CellTag : TagType.MoleculeTag,
                            new GeneFeature[]{GeneFeature.CDR3},
                            PreCloneAssemblerParameters.DefaultGConsensusAssemblerParameters,
                            preClonesFile, tempDest.addSuffix("pc.tmp"));
                    SmartProgressReporter.startProgressReport(assemblerRunner);

                    // Pre-clone assembly happens here (file with pre-clones and alignments written as a result)
                    assemblerRunner.run();

                    // Setting report into a big report object
                    report.setPreCloneAssemblerReport(assemblerRunner.getReport());

                    preClones = assemblerRunner.createReader();
                } else
                    // If there are no tags in the data, alignments are just wrapped into pre-clones
                    preClones = PreCloneReader.fromAlignments(alignmentsReader, assemblerParameters.getAssemblingFeatures());

                // Running assembler
                CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(
                        preClones,
                        assembler);
                SmartProgressReporter.startProgressReport(assemblerRunner);

                if (reportBuffers) {
                    StatusReporter reporter = new StatusReporter();
                    reporter.addCustomProviderFromLambda(() ->
                            new StatusReporter.Status(
                                    "Reader buffer: FIXME " /*+ assemblerRunner.getQueueSize()*/,
                                    assemblerRunner.isFinished()));
                    reporter.start();
                }
                assemblerRunner.run();

                // Getting results
                final CloneSet cloneSet = CloneSet.reorder(assemblerRunner.getCloneSet(alignerParameters, tagsInfo), getOrdering());

                // Passing final cloneset to assemble last pieces of statistics for report
                report.onClonesetFinished(cloneSet);

                // Writing results
                PipelineConfiguration pipelineConfiguration = getFullPipelineConfiguration();
                if (isClnaOutput) {
                    try (ClnAWriter writer = new ClnAWriter(pipelineConfiguration, out,
                            tempDest, highCompression)) {

                        // writer will supply current stage and completion percent to the progress reporter
                        SmartProgressReporter.startProgressReport(writer);
                        // Writing clone block

                        writer.writeClones(cloneSet);

                        // Pre-soring alignments
                        try (AlignmentsMappingMerger merged = new AlignmentsMappingMerger(
                                preClones.readAlignments(),
                                assembler.getAssembledReadsPort())) {
                            writer.collateAlignments(merged, assembler.getAlignmentsCount());
                        }

                        writer.writeAlignmentsAndIndex();
                    }
                } else
                    try (ClnsWriter writer = new ClnsWriter(out)) {
                        writer.writeCloneSet(pipelineConfiguration, cloneSet);
                    }

                // Writing report

                report.setFinishMillis(System.currentTimeMillis());

                assert cloneSet.getClones().size() == report.getCloneCount();

                report.setTotalReads(alignmentsReader.getNumberOfReads());

                // Writing report to stout
                System.out.println("============= Report ==============");
                ReportUtil.writeReportToStdout(report);

                if (reportFile != null)
                    ReportUtil.appendReport(reportFile, report);

                if (jsonReport != null)
                    ReportUtil.appendJsonReport(jsonReport, report);
            }
        }
    }

    private static VDJCAlignments setMappingCloneIndex(VDJCAlignments al, int cloneIndex) {
        return al.withCloneIndexAndMappingType(cloneIndex, ReadToCloneMapping.ADDITIONAL_MAPPING_MASK);
    }

    private static final class TagSignature {
        final TagTuple tags;
        final VDJCGeneId gene;

        public TagSignature(TagTuple tags, VDJCGeneId gene) {
            this.tags = tags;
            this.gene = gene;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TagSignature that = (TagSignature) o;
            return tags.equals(that.tags) &&
                    gene.equals(that.gene);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tags, gene);
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
    public static class AssembleConfiguration implements ActionConfiguration<AssembleConfiguration> {
        public final CloneAssemblerParameters assemblerParameters;
        public final boolean clna;
        public final VDJCSProperties.CloneOrdering ordering;

        @JsonCreator
        public AssembleConfiguration(
                @JsonProperty("assemblerParameters") CloneAssemblerParameters assemblerParameters,
                @JsonProperty("clna") boolean clna,
                @JsonProperty("ordering") VDJCSProperties.CloneOrdering ordering) {
            this.assemblerParameters = assemblerParameters;
            this.clna = clna;
            this.ordering = ordering;
        }

        @Override
        public String actionName() {
            return ASSEMBLE_COMMAND_NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AssembleConfiguration that = (AssembleConfiguration) o;
            return clna == that.clna &&
                    Objects.equals(assemblerParameters, that.assemblerParameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assemblerParameters, clna);
        }
    }
}
