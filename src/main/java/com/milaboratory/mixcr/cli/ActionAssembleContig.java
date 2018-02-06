package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.ParallelProcessor;
import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.cli.ProcessException;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.*;
import java.util.*;

/**
 *
 */
public class ActionAssembleContig implements Action {
    private final FullSeqParameters parameters = new FullSeqParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        long beginTimestamp = System.currentTimeMillis();
        FullSeqAssemblerParameters p = FullSeqAssemblerParameters.getByName("default");
        if (!parameters.overrides.isEmpty()) {
            // Perform parameters overriding
            p = JsonOverrider.override(p, FullSeqAssemblerParameters.class, parameters.overrides);
            if (p == null)
                throw new ProcessException("Failed to override some parameter.");
        }

        final FullSeqAssemblerReport report = new FullSeqAssemblerReport();

        FullSeqAssemblerParameters assemblerParameters = p;
        int totalClonesCount = 0;
        List<VDJCGene> genes;
        VDJCAlignerParameters alignerParameters;
        GeneFeature[] assemblingFeatures;
        try (ClnAReader reader = new ClnAReader(parameters.getInputFileName(), VDJCLibraryRegistry.getDefault());
             PrimitivO tmpOut = new PrimitivO(new BufferedOutputStream(new FileOutputStream(parameters.getOutputFileName())))) {

            alignerParameters = reader.getAlignerParameters();
            genes = reader.getGenes();
            assemblingFeatures = reader.getAssemblingFeatures();
            IOUtil.registerGeneReferences(tmpOut, genes, alignerParameters);
            ClnAReader.CloneAlignmentsPort port = reader.clonesAndAlignments();
            SmartProgressReporter.startProgressReport("Assembling", port);

            OutputPort<Clone[]> parallelProcessor = new ParallelProcessor<>(port, cloneAlignments -> {
                FullSeqAssembler fullSeqAssembler = new FullSeqAssembler(assemblerParameters, cloneAlignments.clone, alignerParameters);
                fullSeqAssembler.setReport(report);

                FullSeqAssembler.RawVariantsData rawVariantsData = fullSeqAssembler.calculateRawData(() -> {
                    try {
                        return cloneAlignments.alignments();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                return fullSeqAssembler.callVariants(rawVariantsData);
            }, parameters.threads);

            for (Clone[] clones : CUtils.it(parallelProcessor)) {
                totalClonesCount += clones.length;
                for (Clone cl : clones)
                    tmpOut.writeObject(cl);
            }
        }

        Clone[] clones = new Clone[totalClonesCount];
        try (PrimitivI tmpIn = new PrimitivI(new BufferedInputStream(new FileInputStream(parameters.getOutputFileName())))) {
            IOUtil.registerGeneReferences(tmpIn, genes, alignerParameters);
            int i = 0;
            for (Clone clone : CUtils.it(new PipeDataInputReader<>(Clone.class, tmpIn, totalClonesCount)))
                clones[i++] = clone;
        }

        Arrays.sort(clones, Comparator.comparingDouble(c -> -c.getCount()));
        for (int i = 0; i < clones.length; i++)
            clones[i] = clones[i].setId(i);
        CloneSet cloneSet = new CloneSet(Arrays.asList(clones), genes, alignerParameters.getFeaturesToAlignMap(), assemblingFeatures);

        try (CloneSetIO.CloneSetWriter writer = new CloneSetIO.CloneSetWriter(cloneSet, parameters.getOutputFileName())) {
            SmartProgressReporter.startProgressReport(writer);
            writer.write();
        }

        ReportWrapper reportWrapper = new ReportWrapper(command(), report);
        reportWrapper.setStartMillis(beginTimestamp);
        reportWrapper.setInputFiles(parameters.getInputFileName());
        reportWrapper.setOutputFiles(parameters.getOutputFileName());
        reportWrapper.setCommandLine(helper.getCommandLineArguments());
        reportWrapper.setFinishMillis(System.currentTimeMillis());

        // Writing report to stout
        System.out.println("============= Report ==============");
        Util.writeReportToStdout(report);

        if (parameters.report != null)
            Util.writeReport(parameters.report, reportWrapper);

        if (parameters.jsonReport != null)
            Util.writeJsonReport(parameters.jsonReport, reportWrapper);

    }

    @Override
    public String command() {
        return "assembleContigs";
    }

    @Override
    public FullSeqParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "Assembles full sequence.")
    public static class FullSeqParameters extends ActionParametersWithOutput {
        @Parameter(description = "input.clna output.clns", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @Parameter(description = "Processing threads",
                names = {"-t", "--threads"}, validateWith = PositiveInteger.class)
        public int threads = 2;

        @DynamicParameter(names = "-O", description = "Overrides default parameter values.")
        public Map<String, String> overrides = new HashMap<>();

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "JSON report file.",
                names = {"--json-report"})
        public String jsonReport = null;

        String getInputFileName() {
            return parameters.get(0);
        }

        String getOutputFileName() {
            return parameters.get(1);
        }

        @Override
        protected List<String> getOutputFiles() {
            return Arrays.asList(parameters.get(parameters.size() - 1));
        }

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Input and output must be specified.");
            super.validate();
        }
    }
}
