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
import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReaderCloseable;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.reference.*;

import java.io.IOException;
import java.util.*;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;

public class ActionAlign implements Action {
    private final AlignParameters actionParameters = new AlignParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        VDJCAlignerParameters alignerParameters = actionParameters.getAlignerParameters();

        if (!actionParameters.overrides.isEmpty()) {
            alignerParameters = JsonOverrider.override(alignerParameters, VDJCAlignerParameters.class, actionParameters.overrides);
            if (alignerParameters == null) {
                System.err.println("Failed to override some parameter.");
                return;
            }
        }

        VDJCAligner aligner = VDJCAligner.createAligner(alignerParameters,
                actionParameters.isInputPaired(), !actionParameters.noMerge);

        LociLibrary ll = LociLibraryManager.getDefault().getLibrary(actionParameters.ll);
        if (ll == null) {
            System.err.println("Segment library (" + actionParameters.ll + ") not found.");
            return;
        }

        // Checking species
        int speciesId = ll.getSpeciesTaxonId(actionParameters.species);

        if (speciesId == -1)
            speciesId = Species.fromString(actionParameters.species);

        if (speciesId == -1) {
            System.err.println("Can't find species with id: " + actionParameters.species);
            return;
        }

        boolean warnings = false;

        for (Locus locus : actionParameters.getLoci()) {
            LocusContainer lc = ll.getLocus(speciesId, locus);
            if (lc == null) {
                if (params().printWarnings()) {
                    System.err.println("WARNING: No records for " + locus);
                    warnings = true;
                }
                continue;
            }
            for (Allele allele : lc.getAllAlleles()) {
                if (actionParameters.isFunctionalOnly() && !allele.isFunctional())
                    continue;
                if (!alignerParameters.containsRequiredFeature(allele)) {
                    if (params().printWarnings()) {
                        System.err.println("WARNING: Allele " + allele.getName() +
                                " doesn't contain full " + GeneFeature.encode(alignerParameters
                                .getFeatureToAlign(allele.getGeneType())) + " (excluded)");
                        warnings = true;
                    }
                    continue;
                }
                aligner.addAllele(allele);
            }
        }

        if (warnings)
            System.err.println("To turn off warnings use '-nw' option.");

        if (aligner.getVAllelesToAlign().isEmpty()) {
            System.err.println("No V alleles to align. Aborting execution. See warnings for more info " +
                    "(turn warnings by adding -w option).");
            return;
        }

        if (aligner.getVAllelesToAlign().isEmpty()) {
            System.err.println("No J alleles to align. Aborting execution. See warnings for more info " +
                    "(turn warnings by adding -w option).");
            return;
        }

        AlignerReport report = actionParameters.report == null ? null : new AlignerReport();
        if (report != null) {
            aligner.setEventsListener(report);
            report.setAllowDifferentVJLoci(actionParameters.allowDifferentVJLoci);
        }

        try (SequenceReaderCloseable<? extends SequenceRead> reader = actionParameters.createReader();
             VDJCAlignmentsWriter writer = actionParameters.getOutputName().equals(".") ? null : new VDJCAlignmentsWriter(actionParameters.getOutputName())) {
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
                        alignment = new VDJCAlignments(read.getId(), emptyHits,
                                readsLayout.createTargets(read)[0].targets);
                    else
                        continue;
                }
                if (!alignment.hasSameVJLoci(1)) {
                    if (report != null)
                        report.onAlignmentWithDifferentVJLoci();
                    if (!actionParameters.allowDifferentVJLoci && !writeAllResults)
                        continue;
                }
                if (writer != null) {
                    if (actionParameters.saveReadDescription || actionParameters.saveOriginalReads)
                        alignment.setDescriptions(extractDescription(read));
                    if (actionParameters.saveOriginalReads)
                        alignment.setOriginalSequences(extractNSeqs(read));
                    writer.write(alignment);
                }
            }
            if (writer != null)
                writer.setNumberOfProcessedReads(reader.getNumberOfReads());
        }

        if (report != null)
            Util.writeReport(actionParameters.getInputForReport(), actionParameters.getOutputName(),
                    helper.getCommandLineArguments(), actionParameters.report, report);
    }

    public static String[] extractDescription(SequenceRead r) {
        String[] descrs = new String[r.numberOfReads()];
        for (int i = 0; i < r.numberOfReads(); i++)
            descrs[i] = r.getRead(i).getDescription();
        return descrs;
    }

    public static NSequenceWithQuality[] extractNSeqs(SequenceRead r) {
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

    @Parameters(commandDescription = "Builds alignments with V,D,J and C genes for input sequencing reads.",
            optionPrefixes = "-")
    public static class AlignParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file1 [input_file2] output_file.vdjca", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @DynamicParameter(names = "-O", description = "Overrides base values of parameters.")
        public Map<String, String> overrides = new HashMap<>();

        @Parameter(description = "Segment library to use",
                names = {"-b", "--library"})
        public String ll = "mi";

        @Parameter(description = "Print warnings",
                names = {"-w", "--warnings"})
        public Boolean warnings = null;

        @Parameter(description = "Don't print warnings",
                names = {"-nw", "--no-warnings"})
        public Boolean noWarnings = null;

        @Parameter(description = "Parameters",
                names = {"-p", "--parameters"})
        public String alignerParametersName = "default";

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "Species (organism). Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, etc..",
                names = {"-s", "--species"})
        public String species = "HomoSapiens";

        @Parameter(description = "Immunological loci to align with separated by ','. Available loci: IGH, IGL, IGK, TRA, TRB, TRG, TRD.",
                names = {"-l", "--loci"})
        public String loci = "all";

        @Parameter(description = "Processing threads",
                names = {"-t", "--threads"}, validateWith = PositiveInteger.class)
        public int threads = Runtime.getRuntime().availableProcessors();

        @Parameter(description = "Maximal number of reads to process",
                names = {"-n", "--limit"}, validateWith = PositiveInteger.class)
        public long limit = 0;

        @Parameter(description = "Use only functional alleles.",
                names = {"-u", "--functional"})
        public Boolean functionalOnly = null;

        @Parameter(description = "Do not merge paired reads.",
                names = {"-d", "--noMerge"})
        public Boolean noMerge = false;

        @Parameter(description = "Copy read(s) description line from .fastq or .fasta to .vdjca file (can be then " +
                "exported with -descrR1 and -descrR2 options in exportAlignments action).",
                names = {"-a", "--save-description"})
        public Boolean saveReadDescription = false;

        @Parameter(description = "Write alignment results for all input reads (even if alignment failed).",
                names = {"-v", "--write-all"})
        public Boolean writeAllResults = null;

        @Parameter(description = "Copy original reads (sequences + qualities + descriptions) to .vdjca file.",
                names = {"-g", "--save-reads"})
        public Boolean saveOriginalReads = false;

        @Parameter(description = "Allow alignments with different loci of V and J hits.",
                names = {"-i", "--diff-loci"})
        public Boolean allowDifferentVJLoci = false;

        public String getSpecies() {
            return species;
        }

        //public int getTaxonID() {
        //    return Species.fromStringStrict(species);
        //}

        public VDJCAlignerParameters getAlignerParameters() {
            VDJCAlignerParameters params = VDJCParametersPresets.getByName(alignerParametersName);
            if (params == null)
                throw new ParameterException("Unknown aligner parameters: " + alignerParametersName);
            return params;
        }

        public boolean isFunctionalOnly() {
            return functionalOnly != null && functionalOnly;
        }

        public String getInputForReport() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; ; ++i) {
                builder.append(parameters.get(i));
                if (i == parameters.size() - 2)
                    break;
                builder.append(',');
            }
            return builder.toString();
        }

        public boolean printWarnings() {
            if (warnings != null && noWarnings != null)
                throw new ParameterException("Simultaneous use of -w and -nw.");
            if (warnings == null)
                return !"mi".equals(ll) && noWarnings == null;
            else
                return warnings;
        }

        public Set<Locus> getLoci() {
            return Util.parseLoci(loci);
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
                if (s[s.length - 1].equals("fasta"))
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
            return Arrays.asList(getOutputName());
        }

        @Override
        public void validate() {
            if (parameters.size() > 3)
                throw new ParameterException("Too many input files.");
            if (parameters.size() < 2)
                throw new ParameterException("No output file.");
            super.validate();
        }
    }
}