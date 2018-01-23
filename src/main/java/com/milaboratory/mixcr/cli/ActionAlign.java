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
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Chunk;
import cc.redberry.pipe.util.CountLimitingOutputPort;
import cc.redberry.pipe.util.Indexer;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.cli.ProcessException;
import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.Target;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReaderCloseable;
import com.milaboratory.core.io.sequence.SequenceWriter;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.*;

import java.io.IOException;
import java.util.*;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;

public class ActionAlign implements Action {
    private final AlignParameters actionParameters = new AlignParameters();

    @Override
    @SuppressWarnings("unchecked")
    public void go(ActionHelper helper) throws Exception {
        // FIXME remove in 2.2
        if (actionParameters.printNonFunctionalWarnings())
            System.out.println("WARNING: -wf / --non-functional-warnings option is deprecated, will be removed in 2.2 " +
                    "release. Use --verbose instead.");

        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();

        // Getting aligner parameters
        VDJCAlignerParameters alignerParameters = actionParameters.getAlignerParameters();

        // FIXME remove in 2.3
        if (actionParameters.getSaveOriginalReads()) {
            System.out.println("WARNING: -g / --save-reads option is deprecated, will be removed in 2.3 " +
                    "release. Use -OsaveOriginalReads=true.");
            alignerParameters.setSaveOriginalReads(true);
        }

        // FIXME remove in 2.3
        if (actionParameters.getSaveReadDescription()) {
            System.out.println("WARNING: -a / --save-description option is deprecated, will be removed in 2.3 " +
                    "release. Use -OsaveOriginalReads=true.");
            alignerParameters.setSaveOriginalReads(true);
        }

        if (!actionParameters.overrides.isEmpty()) {
            // Perform parameters overriding
            alignerParameters = JsonOverrider.override(alignerParameters, VDJCAlignerParameters.class, actionParameters.overrides);
            if (alignerParameters == null)
                throw new ProcessException("Failed to override some parameter.");
        }

        // FIXME remove in 2.2
        if (actionParameters.allowDifferentVJLoci != null && actionParameters.allowDifferentVJLoci) {
            System.out.println("Warning: usage of --diff-loci is deprecated. Use -OallowChimeras=true instead.");
            alignerParameters.setAllowChimeras(true);
        }

        // Creating aligner
        VDJCAligner aligner = VDJCAligner.createAligner(alignerParameters,
                actionParameters.isInputPaired(), !actionParameters.getNoMerge());

        // Detect if automatic featureToAlign correction is required
        int totalV = 0, totalVErrors = 0, hasVRegion = 0;
        GeneFeature correctingFeature = alignerParameters.getVAlignerParameters().getGeneFeatureToAlign().hasReversedRegions() ?
                GeneFeature.VRegionWithP :
                GeneFeature.VRegion;

        VDJCLibrary library = VDJCLibraryRegistry.getDefault().getLibrary(actionParameters.library, actionParameters.species);

        System.out.println("Reference library: " + library.getLibraryId());

        // Printing library level warnings, if specified for the library
        if (!library.getWarnings().isEmpty()) {
            System.out.println("Library warnings:");
            for (String l : library.getWarnings())
                System.out.println(l);
        }

        // Printing citation notice, if specified for the library
        if (!library.getCitations().isEmpty()) {
            System.out.println("Please cite:");
            for (String l : library.getCitations())
                System.out.println(l);
        }

        for (VDJCGene gene : library.getGenes(actionParameters.getChains())) {
            if (gene.getGeneType() == GeneType.Variable) {
                totalV++;
                if (!alignerParameters.containsRequiredFeature(gene)) {
                    totalVErrors++;
                    if (gene.getPartitioning().isAvailable(correctingFeature))
                        hasVRegion++;
                }
            }
        }

        // Performing V featureToAlign correction if needed
        if (totalVErrors > totalV * 0.9 && hasVRegion > totalVErrors * 0.8) {
            System.out.println("WARNING: forcing -OvParameters.geneFeatureToAlign=" + GeneFeature.encode(correctingFeature) +
                    " since current gene feature (" + GeneFeature.encode(alignerParameters.getVAlignerParameters().getGeneFeatureToAlign()) + ") is absent in " +
                    Util.PERCENT_FORMAT.format(100.0 * totalVErrors / totalV) + "% of V genes.");
            alignerParameters.getVAlignerParameters().setGeneFeatureToAlign(correctingFeature);
        }

        int numberOfExcludedNFGenes = 0;
        int numberOfExcludedFGenes = 0;
        for (VDJCGene gene : library.getGenes(actionParameters.getChains())) {
            NucleotideSequence featureSequence = alignerParameters.extractFeatureToAlign(gene);

            // exclusionReason is null ==> gene is not excluded
            String exclusionReason = null;
            if (featureSequence == null)
                exclusionReason = "absent " + GeneFeature.encode(alignerParameters.getFeatureToAlign(gene.getGeneType()));
            else if (featureSequence.containsWildcards())
                exclusionReason = "wildcard symbols in " + GeneFeature.encode(alignerParameters.getFeatureToAlign(gene.getGeneType()));

            if (exclusionReason == null)
                aligner.addGene(gene); // If there are no reasons to exclude the gene, adding it to aligner
            else {
                if (gene.isFunctional()) {
                    ++numberOfExcludedFGenes;
                    if (actionParameters.verbose())
                        System.out.println("WARNING: Functional gene " + gene.getName() +
                                " excluded due to " + exclusionReason);
                } else
                    ++numberOfExcludedNFGenes;
            }
        }

        if (actionParameters.printWarnings() && numberOfExcludedFGenes > 0)
            System.out.println("WARNING: " + numberOfExcludedFGenes + " functional genes were excluded, re-run " +
                    "with --verbose option to see the list of excluded genes and exclusion reason.");

        if (actionParameters.verbose() && numberOfExcludedNFGenes > 0)
            System.out.println("WARNING: " + numberOfExcludedNFGenes + " non-functional genes excluded.");

        if (aligner.getVGenesToAlign().isEmpty())
            throw new ProcessException("No V genes to align. Aborting execution. See warnings for more info " +
                    "(turn on verbose warnings by adding --verbose option).");

        if (aligner.getJGenesToAlign().isEmpty())
            throw new ProcessException("No J genes to align. Aborting execution. See warnings for more info " +
                    "(turn on verbose warnings by adding --verbose option).");

        AlignerReport report = new AlignerReport();
        report.setStartMillis(beginTimestamp);
        report.setInputFiles(actionParameters.getInputsForReport());
        report.setOutputFiles(actionParameters.getOutputsForReport());
        report.setCommandLine(helper.getCommandLineArguments());

        // Attaching report to aligner
        aligner.setEventsListener(report);

        try (SequenceReaderCloseable<? extends SequenceRead> reader = actionParameters.createReader();

             VDJCAlignmentsWriter writer = actionParameters.getOutputName().equals(".") ? null : new VDJCAlignmentsWriter(actionParameters.getOutputName());

             SequenceWriter notAlignedWriter = actionParameters.failedReadsR1 == null
                     ? null
                     : (actionParameters.isInputPaired()
                     ? new PairedFastqWriter(actionParameters.failedReadsR1, actionParameters.failedReadsR2)
                     : new SingleFastqWriter(actionParameters.failedReadsR1));
        ) {
            if (writer != null) writer.header(aligner);
            OutputPort<? extends SequenceRead> sReads = reader;
            CanReportProgress progress = (CanReportProgress) reader;
            if (actionParameters.limit != 0) {
                sReads = new CountLimitingOutputPort<>(sReads, actionParameters.limit);
                progress = SmartProgressReporter.extractProgress((CountLimitingOutputPort<?>) sReads);
            }

            final boolean writeAllResults = actionParameters.getWriteAllResults();
            EnumMap<GeneType, VDJCHit[]> emptyHits = new EnumMap<>(GeneType.class);
            for (GeneType gt : GeneType.values())
                if (alignerParameters.getGeneAlignerParameters(gt) != null)
                    emptyHits.put(gt, new VDJCHit[0]);
            final PairedEndReadsLayout readsLayout = alignerParameters.getReadsLayout();

            SmartProgressReporter.startProgressReport("Alignment", progress);
            OutputPort<Chunk<? extends SequenceRead>> mainInputReads = CUtils.buffered((OutputPort) chunked(sReads, 64), 16);
            OutputPort<VDJCAlignmentResult> alignments = unchunked(new ParallelProcessor(mainInputReads, chunked(aligner), actionParameters.threads));
            for (VDJCAlignmentResult result : CUtils.it(
                    new OrderedOutputPort<>(alignments,
                            new Indexer<VDJCAlignmentResult>() {
                                @Override
                                public long getIndex(VDJCAlignmentResult o) {
                                    return o.read.getId();
                                }
                            }))) {
                VDJCAlignments alignment = result.alignment;
                SequenceRead read = result.read;
                if (alignment == null) {
                    if (writeAllResults)
                    // Creating empty alignment object if alignment for current read failed
                    {
                        Target target = readsLayout.createTargets(read)[0];
                        alignment = new VDJCAlignments(emptyHits,
                                target.targets,
                                SequenceHistory.RawSequence.of(read.getId(), target),
                                alignerParameters.isSaveOriginalReads() ? new SequenceRead[]{read} : null);
                    } else {
                        if (notAlignedWriter != null)
                            notAlignedWriter.write(result.read);
                        continue;
                    }
                }

                if (alignment.isChimera())
                    report.onChimera();

                if (writer != null)
                    writer.write(alignment);
            }
            if (writer != null)
                writer.setNumberOfProcessedReads(reader.getNumberOfReads());
        }

        report.setFinishMillis(System.currentTimeMillis());

        // Writing report to stout
        System.out.println("============= Report ==============");
        Util.writeReportToStdout(report);

        if (actionParameters.report != null)
            Util.writeReport(actionParameters.report, report);

        if (actionParameters.jsonReport != null)
            Util.writeJsonReport(actionParameters.jsonReport, report);
    }

    public static String[] extractDescriptions(SequenceRead r) {
        String[] descrs = new String[r.numberOfReads()];
        for (int i = 0; i < r.numberOfReads(); i++)
            descrs[i] = r.getRead(i).getDescription();
        return descrs;
    }

    public static NSequenceWithQuality[] extractSequences(SequenceRead r) {
        NSequenceWithQuality[] seqs = new NSequenceWithQuality[r.numberOfReads()];
        for (int i = 0; i < r.numberOfReads(); i++)
            seqs[i] = r.getRead(i).getData();
        return seqs;
    }

    @Override
    public String command() {
        return "align";
    }

    @Override
    public AlignParameters params() {
        return actionParameters;
    }

    @Parameters(commandDescription = "Builds alignments with V,D,J and C genes for input sequencing reads.")
    public static class AlignParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file1 [input_file2] output_file.vdjca", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @DynamicParameter(names = "-O", description = "Overrides default parameter values.")
        public Map<String, String> overrides = new HashMap<>();

        @Parameter(description = "Specifies segments library for alignment",
                names = {"-b", "--library"})
        public String library = "default";

        // TODO remove in 2.2 release
        @Parameter(description = "Print warnings for non-functional V/D/J/C genes",
                names = {"-wf", "--non-functional-warnings"})
        public Boolean nonFunctionalWarnings = null;

        @Parameter(description = "Verbose warning messages.",
                names = {"--verbose"})
        public Boolean verbose = null;

        @Parameter(description = "Don't print warnings",
                names = {"-nw", "--no-warnings"})
        public Boolean noWarnings = null;

        @Parameter(description = "Parameters",
                names = {"-p", "--parameters"})
        public String alignerParametersName = "default";

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "JSON report file.",
                names = {"--json-report"})
        public String jsonReport = null;

        @Parameter(description = "Species (organism), as specified in library file or taxon id. " +
                "Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, 9606, 10090 etc..",
                names = {"-s", "--species"})
        public String species = "hs";

        @Parameter(description = "Specifies immunological chain gene(s) for alignment. If many, separate by comma ','. " +
                "Available chains: IGH, IGL, IGK, TRA, TRB, TRG, TRD, etc...",
                names = {"-c", "--chains"})
        public String chains = "ALL";

        @Parameter(description = "Processing threads",
                names = {"-t", "--threads"}, validateWith = PositiveInteger.class)
        public int threads = Runtime.getRuntime().availableProcessors();

        @Parameter(description = "Maximal number of reads to process",
                names = {"-n", "--limit"}, validateWith = PositiveInteger.class)
        public long limit = 0;

        @Parameter(description = "Do not merge paired reads.",
                names = {"-d", "--no-merge"})
        public Boolean noMerge;

        @Deprecated
        @Parameter(description = "Copy read(s) description line from .fastq or .fasta to .vdjca file (can then be " +
                "exported with -descrR1 and -descrR2 options in exportAlignments action).",
                names = {"-a", "--save-description"})
        public Boolean saveReadDescription;

        //TODO delete -v in 2.2
        @Parameter(description = "Write alignment results for all input reads (even if alignment has failed).",
                names = {"-v", "--write-all"})
        public Boolean writeAllResults;

        @Deprecated
        @Parameter(description = "Copy original reads (sequences + qualities + descriptions) to .vdjca file.",
                names = {"-g", "--save-reads"})
        public Boolean saveOriginalReads;

        @Parameter(description = "Write not aligned reads (R1).",
                names = {"--not-aligned-R1"})
        public String failedReadsR1 = null;

        @Parameter(description = "Write not aligned reads (R2).",
                names = {"--not-aligned-R2"})
        public String failedReadsR2 = null;

        @Parameter(description = "Allow alignments with different chains of V and J hits.",
                names = {"-i", "--diff-loci"}, hidden = true)
        public Boolean allowDifferentVJLoci = null;


        public String getSpecies() {
            return species;
        }

        public VDJCAlignerParameters getAlignerParameters() {
            VDJCAlignerParameters params = VDJCParametersPresets.getByName(alignerParametersName);
            if (params == null)
                throw new ParameterException("Unknown aligner parameters: " + alignerParametersName);
            return params;
        }

        public String[] getInputsForReport() {
            return parameters.subList(0, parameters.size() - 1).toArray(new String[parameters.size() - 1]);
        }

        public String[] getOutputsForReport() {
            return new String[]{getOutputName()};
        }

        public Boolean getNoMerge() {
            return noMerge != null && noMerge;
        }

        @Deprecated
        public Boolean getSaveReadDescription() {
            return saveReadDescription != null && saveReadDescription;
        }

        @Deprecated
        public Boolean getSaveOriginalReads() {
            return saveOriginalReads != null && saveOriginalReads;
        }

        @Deprecated
        public boolean printNonFunctionalWarnings() {
            return nonFunctionalWarnings != null && nonFunctionalWarnings;
        }

        public boolean verbose() {
            return (verbose != null && verbose) ||
                    // FIXME remove in 2.2
                    (nonFunctionalWarnings != null && nonFunctionalWarnings);
        }

        public boolean printWarnings() {
            return noWarnings == null || !noWarnings;
        }

        public Chains getChains() {
            return Chains.parse(chains);
        }

        public boolean getWriteAllResults() {
            return writeAllResults != null && writeAllResults;
        }

        public boolean isInputPaired() {
            return parameters.size() == 3;
        }

        public String getOutputName() {
            return parameters.get(parameters.size() - 1);
        }

        public SequenceReaderCloseable<? extends SequenceRead> createReader() throws IOException {
            if (isInputPaired())
                return new PairedFastqReader(parameters.get(0), parameters.get(1), true);
            else {
                String[] s = parameters.get(0).split("\\.");
                if (s[s.length - 1].equals("fasta") || s[s.length - 1].equals("fa"))
                    return new FastaSequenceReaderWrapper(
                            new FastaReader<>(parameters.get(0), NucleotideSequence.ALPHABET),
                            true
                    );
                else
                    return new SingleFastqReader(parameters.get(0), true);
            }
        }

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(getOutputName());
        }

        @Override
        public void validate() {
            if (parameters.size() > 3)
                throw new ParameterException("Too many input files.");
            if (parameters.size() < 2)
                throw new ParameterException("No output file.");
            if (failedReadsR2 != null && failedReadsR1 == null)
                throw new ParameterException("Wrong input for --not-aligned-R1,2");
            if (failedReadsR1 != null && (failedReadsR2 != null) != isInputPaired())
                throw new ParameterException("Option --not-aligned-R2 is not set.");
            if (!printWarnings() && verbose())
                throw new ParameterException("-nw/--no-warnings and --verbose options are not compatible.");
            super.validate();
        }
    }
}