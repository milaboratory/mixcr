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
import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeWriter;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionAssemble implements Action {
    private final AssembleParameters actionParameters = new AssembleParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();

        // Extracting V/D/J/C gene list from input vdjca file
        final List<VDJCGene> genes;
        final VDJCAlignerParameters alignerParameters;
        try(VDJCAlignmentsReader reader = new VDJCAlignmentsReader(actionParameters.getInputFileName(),
                VDJCLibraryRegistry.getDefault())) {
            genes = reader.getUsedGenes();
            // Saving aligner parameters to correct assembler parameters
            alignerParameters = reader.getParameters();
        }

        AlignmentsProvider alignmentsProvider = AlignmentsProvider.Util.createProvider(
                actionParameters.getInputFileName(),
                VDJCLibraryRegistry.getDefault());

        CloneAssemblerParameters assemblerParameters = actionParameters.getCloneAssemblerParameters();
        //set aligner parameters
        assemblerParameters.updateFrom(alignerParameters);

        // Overriding JSON parameters
        if (!actionParameters.overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters, CloneAssemblerParameters.class,
                    actionParameters.overrides);
            if (assemblerParameters == null) {
                System.err.println("Failed to override some parameter.");
                System.exit(1);
            }
        }

        // Performing assembly
        try(CloneAssembler assembler = new CloneAssembler(assemblerParameters,
                actionParameters.readsToClonesMapping != null, genes, alignerParameters.getFeaturesToAlignMap())) {
            // Creating event listener to collect run statistics
            CloneAssemblerReport report = new CloneAssemblerReport();
            assembler.setListener(report);

            // Running assembler
            CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(
                    alignmentsProvider,
                    assembler, actionParameters.threads);
            SmartProgressReporter.startProgressReport(assemblerRunner);
            assemblerRunner.run();

            // Getting results
            final CloneSet cloneSet = assemblerRunner.getCloneSet();

            ChainUsageStats chainsStatistics = new ChainUsageStats();
            for (Clone clone : cloneSet)
                chainsStatistics.put(clone);

            // Writing results
            try(CloneSetIO.CloneSetWriter writer = new CloneSetIO.CloneSetWriter(cloneSet, actionParameters.getOutputFileName())) {
                SmartProgressReporter.startProgressReport(writer);
                writer.write();
            }

            // Writing report
            long time = System.currentTimeMillis() - beginTimestamp;

            assert cloneSet.getClones().size() == report.getCloneCount();

            report.setTotalReads(alignmentsProvider.getTotalNumberOfReads());

            // Writing report to stout
            System.out.println("============= Report ==============");
            Util.writeReportToStdout(time, report, chainsStatistics);

            if (actionParameters.report != null)
                Util.writeReport(actionParameters.getInputFileName(), actionParameters.getOutputFileName(),
                        helper.getCommandLineArguments(), actionParameters.report, time, report, chainsStatistics);

            // Writing raw events (not documented feature)
            if (actionParameters.events != null)
                try(PipeWriter<ReadToCloneMapping> writer = new PipeWriter<>(actionParameters.events)) {
                    CUtils.drain(assembler.getAssembledReadsPort(), writer);
                }

            // Writing Alignment to clone index file
            if (actionParameters.readsToClonesMapping != null)
                AlignmentsToClonesMappingContainer.writeMapping(assembler.getAssembledReadsPort(), cloneSet.size(),
                        actionParameters.readsToClonesMapping);
        }
    }

    @Override
    public String command() {
        return "assemble";
    }

    @Override
    public AssembleParameters params() {
        return actionParameters;
    }

    @Parameters(commandDescription = "Assemble clones")
    public static final class AssembleParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file output_file")
        public List<String> parameters;

        @Parameter(description = "Clone assembling parameters",
                names = {"-p", "--parameters"})
        public String assemblerParametersName = "default";

        @Parameter(description = "Processing threads",
                names = {"-t", "--threads"}, validateWith = PositiveInteger.class)
        public int threads = Runtime.getRuntime().availableProcessors();

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = ".",
                names = {"-e", "--events"}, hidden = true)
        public String events;

        @Parameter(description = ".",
                names = {"-i", "--index"})
        public String readsToClonesMapping;

        @DynamicParameter(names = "-O", description = "Overrides default parameter values.")
        private Map<String, String> overrides = new HashMap<>();

        public String getInputFileName() {
            return parameters.get(0);
        }

        public String getOutputFileName() {
            return parameters.get(1);
        }

        public CloneAssemblerParameters getCloneAssemblerParameters() {
            return CloneAssemblerParametersPresets.getByName(assemblerParametersName);
        }

        @Override
        protected List<String> getOutputFiles() {
            List<String> files = new ArrayList<>();
            files.add(getOutputFileName());
            if (events != null)
                files.add(events);
            return files;
        }

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Wrong number of parameters.");
            if (readsToClonesMapping != null)
                if (new File(readsToClonesMapping).exists() && !isForceOverwrite())
                    throw new ParameterException("File " + readsToClonesMapping + " already exists. Use -f option to overwrite it.");
            super.validate();
        }
    }
}
