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
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.primitives.Filter;
import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeWriter;
import com.milaboratory.util.SmartProgressReporter;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Pump;

import java.io.File;
import java.util.*;

import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.ALIGNMENTS_COMPARATOR;
import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.CLONE_COMPARATOR;

public class ActionAssemble implements Action {
    public static final String MAPDB_SORTED_BY_CLONE = "sortedByClone";
    public static final String MAPDB_SORTED_BY_ALIGNMENT = "sortedByAlignment";
    public static final int MAPDB_BUFFER = 50000;

    private final AssembleParameters actionParameters = new AssembleParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        final List<Allele> alleles;
        final VDJCAlignerParameters alignerParameters;
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(actionParameters.getInputFileName(), LociLibraryManager.getDefault())) {
            alleles = reader.getUsedAlleles();
            // Saving aligner parameters to correct assembler parameters
            alignerParameters = reader.getParameters();
        }

        AlignmentsProvider alignmentsProvider = AlignmentsProvider.Util.createProvider(
                actionParameters.getInputFileName(),
                LociLibraryManager.getDefault());

        CloneAssemblerParameters assemblerParameters = actionParameters.getCloneAssemblerParameters();

        if (!actionParameters.overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters, CloneAssemblerParameters.class,
                    actionParameters.overrides);
            if (assemblerParameters == null){
                System.err.println("Failed to override some parameter.");
                return;
            }
        }

        // Adjusting features to align for correct processing
        for (GeneType geneType : GeneType.values()) {
            GeneFeature featureAssemble = assemblerParameters.getCloneFactoryParameters().getFeatureToAlign(geneType);
            GeneFeature featureAlignment = alignerParameters.getFeatureToAlign(geneType);
            if (featureAssemble == null || featureAlignment == null)
                continue;
            GeneFeature intersection = GeneFeature.intersection(featureAlignment, featureAssemble);
            assemblerParameters.getCloneFactoryParameters().setFeatureToAlign(geneType, intersection);
        }

        // Adjusting features to align for correct processing
        for (GeneType geneType : GeneType.values()) {
            GeneFeature featureAssemble = assemblerParameters.getCloneFactoryParameters().getFeatureToAlign(geneType);
            GeneFeature featureAlignment = alignerParameters.getFeatureToAlign(geneType);
            if (featureAssemble == null || featureAlignment == null)
                continue;
            GeneFeature intersection = GeneFeature.intersection(featureAlignment, featureAssemble);
            assemblerParameters.getCloneFactoryParameters().setFeatureToAlign(geneType, intersection);
        }

        try (CloneAssembler assembler = new CloneAssembler(assemblerParameters, false, alleles)) {

            CloneAssemblerReport report = actionParameters.report == null ? null : new CloneAssemblerReport();
            if (report != null)
                assembler.setListener(report);

            CloneAssemblerRunner assemblerRunner = new CloneAssemblerRunner(
                    alignmentsProvider,
                    assembler, actionParameters.threads);
            SmartProgressReporter.startProgressReport(assemblerRunner);
            assemblerRunner.run();
            try (CloneSetIO.CloneSetWriter writer = new CloneSetIO.CloneSetWriter(assemblerRunner.getCloneSet(), actionParameters.getOutputFileName())) {
                SmartProgressReporter.startProgressReport(writer);
                writer.write();
            }

            if (report != null) {
                report.setTotalReads(alignmentsProvider.getTotalNumberOfReads());
                Util.writeReport(actionParameters.getInputFileName(), actionParameters.getOutputFileName(),
                        helper.getCommandLineArguments(), actionParameters.report, report);
            }

            if (actionParameters.events != null)
                try (PipeWriter<ReadToCloneMapping> writer = new PipeWriter<>(actionParameters.events)) {
                    CUtils.drain(assembler.getAssembledReadsPort(), writer);
                }

            if (actionParameters.readsToClonesMapping != null) {
                File dbFile = new File(actionParameters.readsToClonesMapping);
                if (dbFile.exists()) {
                    dbFile.delete();
                    dbFile = new File(actionParameters.readsToClonesMapping);
                }

                DB db = DBMaker.newFileDB(dbFile)
                        .transactionDisable()
                        .make();

                //byClones
                db.createTreeSet(MAPDB_SORTED_BY_CLONE)
                        .pumpSource(Pump.sort(
                                source(assembler.getAssembledReadsPort()),
                                true, MAPDB_BUFFER,
                                Collections.reverseOrder(CLONE_COMPARATOR),
                                IO.MAPDB_SERIALIZER))
                        .serializer(new IO.ReadToCloneMappingBtreeSerializer(CLONE_COMPARATOR))
                        .comparator(CLONE_COMPARATOR)
                        .make();

                //byAlignments
                db.createTreeSet(MAPDB_SORTED_BY_ALIGNMENT)
                        .pumpSource(Pump.sort(
                                source(assembler.getAssembledReadsPort()),
                                true, MAPDB_BUFFER,
                                Collections.reverseOrder(ALIGNMENTS_COMPARATOR),
                                IO.MAPDB_SERIALIZER))
                        .serializer(new IO.ReadToCloneMappingBtreeSerializer(ALIGNMENTS_COMPARATOR))
                        .comparator(ALIGNMENTS_COMPARATOR)
                        .make();

                db.commit();
                db.close();
            }
        }
    }

    private static Iterator<ReadToCloneMapping> source(OutputPortCloseable<ReadToCloneMapping> assemblerReadsPort) {
        return new CUtils.OPIterator<>(new FilteringPort<>(assemblerReadsPort,
                new Filter<ReadToCloneMapping>() {
                    @Override
                    public boolean accept(ReadToCloneMapping object) {
                        return !object.isDropped();
                    }
                }));
    }

    @Override
    public String command() {
        return "assemble";
    }

    @Override
    public AssembleParameters params() {
        return actionParameters;
    }

    @Parameters(commandDescription = "Assemble clones",
            optionPrefixes = "-")
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
                names = {"-i", "--index"}, hidden = true)
        public String readsToClonesMapping;

        @DynamicParameter(names = "-O", description = "Overrides base values of parameters.")
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
