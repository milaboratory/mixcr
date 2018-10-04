package com.milaboratory.mixcr.cli.newcli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.util.StatusReporter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.CloneAssemblerReport;
import com.milaboratory.mixcr.cli.JsonOverrider;
import com.milaboratory.mixcr.cli.Util;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeWriter;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.milaboratory.mixcr.cli.newcli.CommandAssemble.ASSEMBLE_COMMAND_NAME;

@Command(name = ASSEMBLE_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Assemble clones.")
public class CommandAssemble extends ACommandWithResumeWithSingleInput {
    static final String ASSEMBLE_COMMAND_NAME = "assemble";

    @Option(description = "Clone assembling parameters",
            names = {"-p", "--parameters"})
    public String assemblerParametersName = "default";

    public int threads = Runtime.getRuntime().availableProcessors();

    @Option(description = "Processing threads",
            names = {"-t", "--threads"})
    public void setThreads(int threads) {
        if (threads <= 0)
            throwValidationException("-t / --threads must be positive");
        this.threads = threads;
    }

    @Option(description = "Report file",
            names = {"-r", "--report"})
    public String reportFile;

    @Option(description = "JSON report file.",
            names = {"--json-report"})
    public String jsonReport;

    @Option(description = "Buffers.",
            names = {"--buffers"}, hidden = true)
    public boolean reportBuffers;

    @Option(description = ".",
            names = {"-e", "--events"}, hidden = true)
    public String events;

    @Option(description = "If this option is specified, output file will be written in \"Clones & " +
            "Alignments\" format (*.clna), containing clones and all corresponding alignments. " +
            "This file then can be used to build wider contigs for clonal sequence and extract original " +
            "reads for each clone (if -OsaveOriginalReads=true was use on 'align' stage).",
            names = {"-a", "--write-alignments"})
    public boolean clna = false;

    @Option(names = "-O", description = "Overrides default parameter values.")
    private Map<String, String> overrides = new HashMap<>();


    @Override
    public ActionConfiguration getConfiguration() {
        return new AssembleConfiguration(getCloneAssemblerParameters(), clna);
    }

    // Extracting V/D/J/C gene list from input vdjca file
    private List<VDJCGene> genes = null;
    private VDJCAlignerParameters alignerParameters = null;
    private CloneAssemblerParameters assemblerParameters = null;

    private void initializeParameters() {
        if (assemblerParameters != null)
            return;

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in,
                VDJCLibraryRegistry.getDefault())) {
            genes = reader.getUsedGenes();
            // Saving aligner parameters to correct assembler parameters
            alignerParameters = reader.getParameters();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assert alignerParameters != null;

        //set aligner parameters
        assemblerParameters = CloneAssemblerParametersPresets.getByName(assemblerParametersName);
        if (assemblerParameters == null)
            throwValidationException("Unknown parameters: " + assemblerParametersName);
        assemblerParameters = assemblerParameters.updateFrom(alignerParameters);

        // Overriding JSON parameters
        if (!overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters, CloneAssemblerParameters.class,
                    overrides);
            if (assemblerParameters == null)
                throwValidationException("Failed to override some parameter: " + overrides);
        }
    }

    public CloneAssemblerParameters getCloneAssemblerParameters() {
        initializeParameters();
        return assemblerParameters;
    }

    public List<VDJCGene> getGenes() {
        initializeParameters();
        return genes;
    }

    public VDJCAlignerParameters getAlignerParameters() {
        initializeParameters();
        return alignerParameters;
    }

    @Override
    public void validate() {
        super.validate();
        if (events != null && new File(events).exists())
            handleExistenceOfOutputFile(events);
    }

    /**
     * Assemble report
     */
    public final CloneAssemblerReport report = new CloneAssemblerReport();

    @Override
    public void run1() throws Exception {
        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();

        // Checking consistency between actionParameters.doWriteClnA() value and file extension
        if ((getOutput().toLowerCase().endsWith(".clna") && !clna) ||
                (getOutput().toLowerCase().endsWith(".clns") && clna))
            warn("WARNING: Unexpected file extension, use .clns extension for clones-only (normal) output and\n" +
                    ".clna if -a / --write-alignments options specified.");


        AlignmentsProvider alignmentsProvider = AlignmentsProvider.Util.createProvider(
                in, VDJCLibraryRegistry.getDefault());

        CloneAssemblerParameters assemblerParameters = getCloneAssemblerParameters();
        List<VDJCGene> genes = getGenes();
        VDJCAlignerParameters alignerParameters = getAlignerParameters();

        // Performing assembly
        try (CloneAssembler assembler = new CloneAssembler(assemblerParameters,
                clna || events != null,
                genes, alignerParameters.getFeaturesToAlignMap())) {
            // Creating event listener to collect run statistics
            report.setStartMillis(beginTimestamp);
            report.setInputFiles(in);
            report.setOutputFiles(out);
            report.setCommandLine(getCommandLineArguments());

            assembler.setListener(report);

            // Running assembler
            CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(
                    alignmentsProvider,
                    assembler, threads);
            SmartProgressReporter.startProgressReport(assemblerRunner);

            if (reportBuffers) {
                StatusReporter reporter = new StatusReporter();
                reporter.addCustomProviderFromLambda(() ->
                        new StatusReporter.Status(
                                "Reader buffer: " + assemblerRunner.getQueueSize(),
                                assemblerRunner.isFinished()));
                reporter.start();
            }
            assemblerRunner.run();

            // Getting results
            final CloneSet cloneSet = assemblerRunner.getCloneSet(alignerParameters);

            // Passing final cloneset to assemble last pieces of statistics for report
            report.onClonesetFinished(cloneSet);

            // Writing results
            PipelineConfiguration pipelineConfiguration = getFullPipelineConfiguration();
            if (clna)
                try (ClnAWriter writer = new ClnAWriter(pipelineConfiguration, out)) {
                    // writer will supply current stage and completion percent to the progress reporter
                    SmartProgressReporter.startProgressReport(writer);
                    // Writing clone block

                    writer.writeClones(cloneSet);
                    // Pre-soring alignments
                    try (AlignmentsMappingMerger merged = new AlignmentsMappingMerger(alignmentsProvider.create(),
                            assembler.getAssembledReadsPort())) {
                        writer.sortAlignments(merged, assembler.getAlignmentsCount());
                    }
                    writer.writeAlignmentsAndIndex();
                }
            else
                try (ClnsWriter writer = new ClnsWriter(pipelineConfiguration, cloneSet, out)) {
                    SmartProgressReporter.startProgressReport(writer);
                    writer.write();
                }

            // Writing report

            report.setFinishMillis(System.currentTimeMillis());

            assert cloneSet.getClones().size() == report.getCloneCount();

            report.setTotalReads(alignmentsProvider.getTotalNumberOfReads());

            // Writing report to stout
            System.out.println("============= Report ==============");
            Util.writeReportToStdout(report);

            if (reportFile != null)
                Util.writeReport(reportFile, report);

            if (jsonReport != null)
                Util.writeJsonReport(jsonReport, report);

            // Writing raw events (not documented feature)
            if (events != null)
                try (PipeWriter<ReadToCloneMapping> writer = new PipeWriter<>(events)) {
                    CUtils.drain(assembler.getAssembledReadsPort(), writer);
                }
        }
    }

    public static class AssembleConfiguration implements ActionConfiguration {
        public final CloneAssemblerParameters assemblerParameters;
        public final boolean clna;

        @JsonCreator
        public AssembleConfiguration(
                @JsonProperty("assemblerParameters") CloneAssemblerParameters assemblerParameters,
                @JsonProperty("clna") boolean clna) {
            this.assemblerParameters = assemblerParameters;
            this.clna = clna;
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
