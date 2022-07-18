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

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.Processor;
import cc.redberry.pipe.blocks.Merger;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Chunk;
import cc.redberry.pipe.util.CountLimitingOutputPort;
import cc.redberry.pipe.util.OrderedOutputPort;
import cc.redberry.pipe.util.StatusReporter;
import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.Target;
import com.milaboratory.core.io.CompressionType;
import com.milaboratory.core.io.sequence.*;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.quality.QualityTrimmerParameters;
import com.milaboratory.core.sequence.quality.ReadTrimmerProcessor;
import com.milaboratory.milm.MiXCRMain;
import com.milaboratory.mitool.helpers.FSKt;
import com.milaboratory.mitool.pattern.PatternCollection;
import com.milaboratory.mitool.pattern.search.*;
import com.milaboratory.mitool.report.ParseReport;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.*;
import com.milaboratory.mixcr.vdjaligners.*;
import com.milaboratory.util.*;
import io.repseq.core.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;
import static com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter.DEFAULT_ALIGNMENTS_IN_BLOCK;
import static com.milaboratory.mixcr.basictypes.tag.TagType.*;
import static com.milaboratory.mixcr.basictypes.tag.TagValueType.SequenceAndQuality;
import static com.milaboratory.mixcr.cli.CommandAlign.ALIGN_COMMAND_NAME;

@Command(name = ALIGN_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Builds alignments with V,D,J and C genes for input sequencing reads.")
public class CommandAlign extends MiXCRCommand {
    static final String ALIGN_COMMAND_NAME = "align";
    @Parameters(arity = "2..3",
            paramLabel = "files",
            hideParamSyntax = true,
            description = "file_R1.(fastq[.gz]|fasta) [file_R2.fastq[.gz]] alignments.vdjca\n" +
                    "Use \"{{n}}\" if you want to concatenate files from multiple lanes, like:\n" +
                    "my_file_L{{n}}_R1.fastq.gz my_file_L{{n}}_R2.fastq.gz")
    private List<String> inOut = new ArrayList<>();

    @Override
    public List<String> getInputFiles() {
        return inOut.subList(0, inOut.size() - 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        return inOut.subList(inOut.size() - 1, inOut.size());
    }

    @Option(description = CommonDescriptions.SPECIES,
            names = {"--read-buffer"})
    public int readBufferSize = 1 << 22;

    @Option(description = CommonDescriptions.SPECIES,
            names = {"-s", "--species"},
            required = true)
    public String species = null;

    @Option(description = CommonDescriptions.REPORT,
            names = {"-r", "--report"})
    public String reportFile = null;

    @Option(description = CommonDescriptions.JSON_REPORT,
            names = {"-j", "--json-report"})
    public String jsonReport = null;

    @Option(description = "V/D/J/C gene library",
            names = {"-b", "--library"})
    public String library = "default";

    public int threads = Runtime.getRuntime().availableProcessors();

    @Option(description = "Processing threads",
            names = {"-t", "--threads"})
    public void setThreads(int threads) {
        if (threads <= 0)
            throwValidationException("ERROR: -t / --threads must be positive", false);
        this.threads = threads;
    }

    @Option(description = "Use higher compression for output file, 10~25%% slower, minus 30~50%% of file size.",
            names = {"--high-compression"})
    public boolean highCompression = false;

    public long limit = 0;

    @Option(description = "Maximal number of reads to process",
            names = {"-n", "--limit"})
    public void setLimit(int limit) {
        if (limit <= 0)
            throwValidationException("ERROR: -n / --limit must be positive", false);
        this.limit = limit;
    }

    @Option(description = "Read pre-processing: trimming quality threshold",
            names = {"--trimming-quality-threshold"})
    public byte trimmingQualityThreshold = 0; // 17

    @Option(description = "Read pre-processing: trimming window size",
            names = {"--trimming-window-size"})
    public byte trimmingWindowSize = 6; // 3

    @Option(description = "Parameters preset.",
            names = {"-p", "--preset"})
    public String alignerParametersName = "default";

    @Option(names = {"-O"}, description = "Overrides default aligner parameter values")
    public Map<String, String> overrides = new HashMap<>();

    public String chains = "ALL";

    @Option(description = "Specifies immunological chain / gene(s) for alignment. If many, separate by comma ','. " +
            "%nAvailable chains: IGH, IGL, IGK, TRA, TRB, TRG, TRD, etc...",
            names = {"-c", "--chains"},
            hidden = true)
    public void setChains(String chains) {
        warn("Don't use --chains option on the alignment step. See --chains parameter in exportAlignments and " +
                "exportClones actions to limit output to a subset of receptor chains.");
        this.chains = chains;
    }

    @Option(description = "Do not merge paired reads.",
            names = {"-d", "--no-merge"},
            hidden = true)
    public boolean noMerge = false;

    @Deprecated
    @Option(description = "Copy read(s) description line from .fastq or .fasta to .vdjca file (can then be " +
            "exported with -descrR1 and -descrR2 options in exportAlignments action).",
            names = {"-a", "--save-description"},
            hidden = true)
    public void setSaveReadDescription(boolean b) {
        throwValidationException("--save-description was removed in 3.0: use -OsaveOriginalReads=true instead");
    }

    @Option(description = "Write alignment results for all input reads (even if alignment failed).",
            names = {"--write-all"})
    public boolean writeAllResults = false;

    @Deprecated
    @Option(description = "Copy original reads (sequences + qualities + descriptions) to .vdjca file.",
            names = {"-g", "--save-reads"},
            hidden = true)
    public void setSaveOriginalReads(boolean b) {
        throwValidationException("--save-reads was removed in 3.0: use -OsaveOriginalReads=true instead");
    }

    @Option(description = "Pipe not aligned R1 reads into separate file.",
            names = {"--not-aligned-R1"})
    public String failedReadsR1 = null;

    @Option(description = "Pipe not aligned R2 reads into separate file.",
            names = {"--not-aligned-R2"})
    public String failedReadsR2 = null;

    @Option(description = "Show runtime buffer load.",
            names = {"--buffers"}, hidden = true)
    public boolean reportBuffers = false;

    // @Option(description = "Specify this option for 10x datasets to extract cell and UMI barcode information from " +
    //         "the first read",
    //         names = {"--10x"})
    // public boolean tenX = false;

    @Option(description = "Tag pattern to extract from the read.",
            names = {"--tag-pattern"})
    public String tagPattern;

    @Option(description = "Tag pattern name from the built-in list.",
            names = {"--tag-pattern-name"})
    public String tagPatternName;

    @Option(description = "Read tag pattern from a file.",
            names = {"--tag-pattern-file"})
    public String tagPatternFile;

    @Option(description = "If paired-end input is used, determines whether to try all combinations of mate-pairs or only match " +
            "reads to the corresponding pattern sections (i.e. first file to first section, etc...)",
            names = {"--tag-parse-unstranded"})
    public boolean tagUnstranded = false;

    @Option(description = "Maximal bit budget, higher values allows more substitutions in small letters.",
            names = {"--tag-max-budget"})
    public double tagMaxBudget = 10.0;

    private VDJCAlignerParameters vdjcAlignerParameters = null;

    public VDJCAlignerParameters getAlignerParameters() {
        if (vdjcAlignerParameters != null)
            return vdjcAlignerParameters;

        VDJCAlignerParameters alignerParameters;
        if (alignerParametersName.endsWith(".json")) {
            try {
                alignerParameters = GlobalObjectMappers.getOneLine().readValue(new File(alignerParametersName), VDJCAlignerParameters.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            alignerParameters = VDJCParametersPresets.getByName(alignerParametersName);
            if (alignerParameters == null)
                throwValidationException("Unknown aligner parameters: " + alignerParametersName);

            if (!overrides.isEmpty()) {
                // Printing warning message for some common mistakes in parameter overrides
                for (Map.Entry<String, String> o : overrides.entrySet())
                    if ("Parameters.parameters.relativeMinScore".equals(o.getKey().substring(1)))
                        warn("WARNING: most probably you want to change \"" + o.getKey().charAt(0) +
                                "Parameters.relativeMinScore\" instead of \"" + o.getKey().charAt(0) +
                                "Parameters.parameters.relativeMinScore\". " +
                                "The latter should be touched only in a very specific cases.");

                // Perform parameters overriding
                alignerParameters = JsonOverrider.override(alignerParameters, VDJCAlignerParameters.class, overrides);
                if (alignerParameters == null)
                    throwValidationException("Failed to override some parameter: " + overrides);
            }
        }

        // Detect if automatic featureToAlign correction is required
        VDJCLibrary library = getLibrary();

        int totalV = 0, totalVErrors = 0, hasVRegion = 0;
        GeneFeature correctingFeature = alignerParameters.getVAlignerParameters().getGeneFeatureToAlign().hasReversedRegions() ?
                GeneFeature.VRegionWithP :
                GeneFeature.VRegion;

        for (VDJCGene gene : library.getGenes(getChains())) {
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
            warn("WARNING: forcing -OvParameters.geneFeatureToAlign=" + GeneFeature.encode(correctingFeature) +
                    " since current gene feature (" + GeneFeature.encode(alignerParameters.getVAlignerParameters().getGeneFeatureToAlign()) + ") is absent in " +
                    ReportHelper.PERCENT_FORMAT.format(100.0 * totalVErrors / totalV) + "% of V genes.");
            alignerParameters.getVAlignerParameters().setGeneFeatureToAlign(correctingFeature);
        }

        return vdjcAlignerParameters = alignerParameters;
    }

    public Chains getChains() {
        return Chains.parse(chains);
    }

    private VDJCLibrary vdjcLibrary = null;

    static final Pattern libraryNameEnding = Pattern.compile("\\.json(?:\\.gz|)$");

    public String getLibraryName() {
        return libraryNameEnding.matcher(library).replaceAll("");
    }

    public VDJCLibrary getLibrary() {
        return vdjcLibrary != null
                ? vdjcLibrary
                : (vdjcLibrary = VDJCLibraryRegistry.getDefault().getLibrary(getLibraryName(), species));
    }

    public boolean taggedAnalysis() {
        return tagPattern != null || tagPatternName != null || tagPatternFile != null;
    }

    public boolean isInputPaired() {
        return getInputFiles().size() == 2;
    }

    public SequenceReaderCloseable<? extends SequenceRead> createReader() throws IOException {
        // Common single fastq reader constructor
        Function<Path, SingleFastqReader> readerFactory = r -> {
            try {
                return new SingleFastqReader(
                        new FileInputStream(r.toFile()),
                        SingleFastqReader.DEFAULT_QUALITY_FORMAT,
                        CompressionType.detectCompressionType(getInputFiles().get(0)),
                        false, readBufferSize,
                        true, true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        if (isInputPaired()) {
            List<List<Path>> resolved = getInputFiles().stream()
                    .map(rf -> FSKt.expandPathNPattern(Paths.get(rf)))
                    .collect(Collectors.toList());
            MiXCRMain.lm.reportApplicationInputs(resolved.stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList()));
            List<ConcatenatingSingleReader> readers =
                    resolved.stream()
                            .map(rs -> new ConcatenatingSingleReader(
                                    rs.stream()
                                            .map(readerFactory)
                                            .collect(Collectors.toList())
                            ))
                            .collect(Collectors.toList());
            return new PairedFastqReader(readers.get(0), readers.get(1));
        } else {
            String in = getInputFiles().get(0);
            String[] s = in.split("\\.");
            if (s[s.length - 1].equals("fasta") || s[s.length - 1].equals("fa"))
                return new FastaSequenceReaderWrapper(
                        new FastaReader<>(in, NucleotideSequence.ALPHABET),
                        true
                );
            else {
                List<Path> resolved = FSKt.expandPathNPattern(Paths.get(in));
                MiXCRMain.lm.reportApplicationInputs(resolved);
                return new ConcatenatingSingleReader(resolved.stream()
                        .map(readerFactory)
                        .collect(Collectors.toList()));
            }
        }
    }

    public TagSearchPlan getTagPattern() {
        if (tagPattern == null && tagPatternName == null && tagPatternFile == null)
            return null;

        if ((tagPattern != null ? 1 : 0) + (tagPatternName != null ? 1 : 0) + (tagPatternFile != null ? 1 : 0) != 1)
            throwValidationException("--tag-pattern, --tag-pattern-name and --tag-pattern-file can't be used together");

        String tagPattern;
        if (this.tagPattern != null)
            tagPattern = this.tagPattern;
        else if (this.tagPatternName != null)
            tagPattern = PatternCollection.INSTANCE.getPatternByName(this.tagPatternName);
        else if (this.tagPatternFile != null)
            try {
                tagPattern = new String(Files.readAllBytes(Paths.get(this.tagPatternFile)));
            } catch (IOException e) {
                throwValidationException(e.getMessage());
                throw new AssertionError();
            }
        else
            throw new AssertionError();

        System.out.println("Tags will be extracted using the following pattern:");
        System.out.println(tagPattern);

        ReadSearchSettings searchSettings = new ReadSearchSettings(new SearchSettings(tagMaxBudget, 0.1,
                new MatcherSettings(3, 7)),
                isInputPaired()
                        ? tagUnstranded
                        ? ReadSearchMode.PairedUnknown
                        : ReadSearchMode.PairedDirect
                        : ReadSearchMode.Single);
        ReadSearchPlan readSearchPlan = ReadSearchPlan.Companion.create(tagPattern, searchSettings);
        ParseInfo parseInfo = parseTagsFromSet(readSearchPlan.getAllTags());

        System.out.println("The following tags and their roles were recognised:");
        System.out.println("  Payload tags: " + String.join(", ", parseInfo.getReadTags()));
        parseInfo.tags.stream().collect(Collectors.groupingBy(TagInfo::getType))
                .forEach((tagType, tagInfos) ->
                        System.out.println("  " + tagType + " tags: " + tagInfos.stream().map(TagInfo::getName).collect(Collectors.joining()))
                );

        List<ReadTagShortcut> tagShortcuts = parseInfo.getTags().stream()
                .map(t -> readSearchPlan.tagShortcut(t.getName()))
                .collect(Collectors.toList());
        List<ReadTagShortcut> readShortcuts = parseInfo.getReadTags().stream()
                .map(readSearchPlan::tagShortcut)
                .collect(Collectors.toList());

        if (readShortcuts.size() == 0)
            throwValidationException("Tag pattern has no read (payload) groups, nothing to align.", false);

        if (readShortcuts.size() > 2)
            throwValidationException("Tag pattern contains too many read groups, only R1 or R1+R2 combinations are supported.", false);

        if (failedReadsR1 != null) {
            if (failedReadsR2 == null && readShortcuts.size() == 2)
                throwValidationException("Option --not-aligned-R2 is not specified but tag pattern defines two payload reads.", false);
            if (failedReadsR2 != null && readShortcuts.size() == 1)
                throwValidationException("Option --not-aligned-R2 is specified but tag pattern defines only one payload read.", false);
        }

        return new TagSearchPlan(readSearchPlan, tagShortcuts, readShortcuts, parseInfo.getTags());
    }

    @Override
    protected boolean inputsMustExist() {
        return false;
    }

    @Override
    public void validate() {
        super.validate();
        if (inOut.size() > 3)
            throwValidationException("Too many input files.");
        if (inOut.size() < 2)
            throwValidationException("No output file.");
        if (failedReadsR2 != null && failedReadsR1 == null)
            throwValidationException("Wrong input for --not-aligned-R1,2");
        if (failedReadsR1 != null && !taggedAnalysis() && (failedReadsR2 != null) != isInputPaired())
            throwValidationException("Option --not-aligned-R2 is not set.", false);
        if (library.contains("/") || library.contains("\\"))
            throwValidationException("Library name can't be a path. Place your library to one of the " +
                    "library search locations (e.g. '" + Paths.get(System.getProperty("user.home"), ".mixcr", "libraries", "mylibrary.json").toString() +
                    "', and put just a library name as -b / --library option value (e.g. '--library mylibrary').", false);
    }

    /** Alignment report */
    public final AlignerReportBuilder reportBuilder = new AlignerReportBuilder();

    private QualityTrimmerParameters getQualityTrimmerParameters() {
        return new QualityTrimmerParameters(trimmingQualityThreshold,
                trimmingWindowSize);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void run0() throws Exception {
        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();

        // Getting aligner parameters
        VDJCAlignerParameters alignerParameters = getAlignerParameters();

        // Detect if automatic featureToAlign correction is required
        VDJCLibrary library = getLibrary();

        // Printing library level warnings, if specified for the library
        if (!library.getWarnings().isEmpty()) {
            warn("Library warnings:");
            for (String l : library.getWarnings())
                warn(l);
        }

        // Printing citation notice, if specified for the library
        if (!library.getCitations().isEmpty()) {
            warn("Please cite:");
            for (String l : library.getCitations())
                warn(l);
        }

        // Tags
        TagSearchPlan tagSearchPlan = getTagPattern();

        // Creating aligner
        VDJCAligner aligner = VDJCAligner.createAligner(alignerParameters,
                tagSearchPlan != null ? tagSearchPlan.readShortcuts.size() == 2 : isInputPaired(),
                !noMerge);

        int numberOfExcludedNFGenes = 0;
        int numberOfExcludedFGenes = 0;
        for (VDJCGene gene : library.getGenes(getChains())) {
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
                    if (verbose)
                        warn("WARNING: Functional gene " + gene.getName() + " excluded due to " + exclusionReason);
                } else
                    ++numberOfExcludedNFGenes;
            }
        }

        if (numberOfExcludedFGenes > 0)
            warn("WARNING: " + numberOfExcludedFGenes + " functional genes were excluded, re-run " +
                    "with --verbose option to see the list of excluded genes and exclusion reason.");

        if (verbose && numberOfExcludedNFGenes > 0)
            warn("WARNING: " + numberOfExcludedNFGenes + " non-functional genes excluded.");

        if (aligner.getVGenesToAlign().isEmpty())
            throwExecutionException("No V genes to align. Aborting execution. See warnings for more info " +
                    "(turn on verbose warnings by adding --verbose option).");

        if (aligner.getJGenesToAlign().isEmpty())
            throwExecutionException("No J genes to align. Aborting execution. See warnings for more info " +
                    "(turn on verbose warnings by adding --verbose option).");

        reportBuilder.setStartMillis(beginTimestamp);
        reportBuilder.setInputFiles(getInputFiles());
        reportBuilder.setOutputFiles(getOutputFiles());
        reportBuilder.setCommandLine(getCommandLineArguments());

        if (tagSearchPlan != null)
            reportBuilder.setTagReportBuilder(tagSearchPlan.report);

        // Attaching report to aligner
        aligner.setEventsListener(reportBuilder);

        String outputFile = getOutputFiles().get(0);
        try (SequenceReaderCloseable<? extends SequenceRead> reader = createReader();

             VDJCAlignmentsWriter writer = outputFile.equals(".")
                     ? null
                     : new VDJCAlignmentsWriter(outputFile, Math.max(1, threads / 8),
                     DEFAULT_ALIGNMENTS_IN_BLOCK, highCompression);

             SequenceWriter notAlignedWriter = failedReadsR1 == null
                     ? null
                     : (isInputPaired()
                     ? new PairedFastqWriter(failedReadsR1, failedReadsR2)
                     : new SingleFastqWriter(failedReadsR1));
        ) {
            if (writer != null)
                writer.header(aligner,
                        tagSearchPlan != null
                                ? new TagsInfo(0, tagSearchPlan.tagInfos.toArray(new TagInfo[0]))
                                : TagsInfo.NO_TAGS);

            OutputPort<? extends SequenceRead> sReads = reader;
            CanReportProgress progress = (CanReportProgress) reader;
            if (limit != 0) {
                sReads = new CountLimitingOutputPort<>(sReads, limit);
                progress = SmartProgressReporter.extractProgress((CountLimitingOutputPort<?>) sReads);
            }

            // Shifting indels in homopolymers is effective only for alignments build with linear gap scoring,
            // consolidating some gaps, on the contrary, for alignments obtained with affine scoring such procedure
            // may break the alignment (gaps there are already consolidated as much as possible)
            Set<GeneType> gtRequiringIndelShifts = alignerParameters.getGeneTypesWithLinearScoring();

            EnumMap<GeneType, VDJCHit[]> emptyHits = new EnumMap<>(GeneType.class);
            for (GeneType gt : GeneType.values())
                if (alignerParameters.getGeneAlignerParameters(gt) != null)
                    emptyHits.put(gt, new VDJCHit[0]);
            final PairedEndReadsLayout readsLayout = alignerParameters.getReadsLayout();

            SmartProgressReporter.startProgressReport("Alignment", progress);
            Merger<Chunk<? extends SequenceRead>> mainInputReads = CUtils.buffered((OutputPort) chunked(sReads, 64), Math.max(16, threads));

            OutputPort<Chunk<? extends SequenceRead>> mainInputReadsPreprocessed = mainInputReads;

            ReadTrimmerProcessor readTrimmerProcessor;
            if (trimmingQualityThreshold > 0) {
                ReadTrimmerReportBuilder rep = new ReadTrimmerReportBuilder();
                readTrimmerProcessor = new ReadTrimmerProcessor(getQualityTrimmerParameters(), rep);
                reportBuilder.setTrimmingReportBuilder(rep);
            } else
                readTrimmerProcessor = null;

            // Creating processor from aligner
            Processor<SequenceRead, VDJCAlignmentResult<SequenceRead>> processor = aligner;
            if (tagSearchPlan != null) {
                final Processor<SequenceRead, VDJCAlignmentResult<SequenceRead>> oldProcessor = processor;
                processor = input -> {
                    TaggedSequence parsed = tagSearchPlan.parse(input);

                    if (parsed == null) {
                        reportBuilder.onFailedAlignment(input, VDJCAlignmentFailCause.NoBarcode);
                        return new VDJCAlignmentResult(input);
                    }

                    SequenceRead read = parsed.payloadRead;
                    if (readTrimmerProcessor != null)
                        read = readTrimmerProcessor.process(read);

                    VDJCAlignmentResult<SequenceRead> alignmentResult = oldProcessor.process(read);
                    return alignmentResult.withTagTuple(parsed.tags);
                };
            } else if (readTrimmerProcessor != null)
                mainInputReadsPreprocessed = CUtils.wrap(mainInputReadsPreprocessed, CUtils.chunked(readTrimmerProcessor));

            ParallelProcessor alignedChunks = new ParallelProcessor(mainInputReadsPreprocessed, chunked(processor), Math.max(16, threads), threads);
            if (reportBuffers) {
                System.out.println("Analysis threads: " + threads);
                StatusReporter reporter = new StatusReporter();
                reporter.addBuffer("Input (chunked; chunk size = 64)", mainInputReads.getBufferStatusProvider());
                reporter.addBuffer("Alignment result (chunked; chunk size = 64)", alignedChunks.getOutputBufferStatusProvider());
                reporter.addCustomProvider(new StatusReporter.StatusProvider() {
                    volatile String status;
                    volatile boolean isClosed = false;

                    @Override
                    public void updateStatus() {
                        status = "Busy encoders: " + Objects.requireNonNull(writer).getBusyEncoders() + " / " + writer.getEncodersCount();
                        isClosed = writer.isClosed();
                    }

                    @Override
                    public boolean isFinished() {
                        return isClosed;
                    }

                    @Override
                    public String getStatus() {
                        return status;
                    }
                });
                reporter.start();
            }

            OutputPort<VDJCAlignmentResult> alignments = unchunked(
                    CUtils.wrap(alignedChunks,
                            CUtils.<VDJCAlignmentResult, VDJCAlignmentResult>chunked(al -> al.shiftIndelsAtHomopolymers(gtRequiringIndelShifts))));

            for (VDJCAlignmentResult result : CUtils.it(new OrderedOutputPort<>(alignments, o -> o.read.getId()))) {
                VDJCAlignments alignment = result.alignment;
                SequenceRead read = result.read;
                if (alignment == null) {
                    if (writeAllResults) { // Creating empty alignment object if alignment for current read failed
                        Target target = readsLayout.createTargets(read)[0];
                        alignment = new VDJCAlignments(emptyHits,
                                result.tagTuple == null ? TagCount.NO_TAGS_1 : new TagCount(result.tagTuple),
                                target.targets,
                                SequenceHistory.RawSequence.of(read.getId(), target),
                                alignerParameters.isSaveOriginalReads() ? new SequenceRead[]{read} : null);
                    } else {
                        if (notAlignedWriter != null)
                            notAlignedWriter.write(result.read);
                        continue;
                    }
                }

                alignment = alignment.setTagCount(result.tagTuple == null ? TagCount.NO_TAGS_1 : new TagCount(result.tagTuple));

                if (alignment.isChimera())
                    reportBuilder.onChimera();

                if (writer != null)
                    writer.write(alignment);
            }
            if (writer != null)
                writer.setNumberOfProcessedReads(reader.getNumberOfReads());

            reportBuilder.setFinishMillis(System.currentTimeMillis());

            AlignerReport report = reportBuilder.buildReport();

            if (writer != null)
                writer.writeFooter(Collections.singletonList(report), null);

            // Writing report to stout
            ReportUtil.writeReportToStdout(report);

            if (reportFile != null)
                ReportUtil.appendReport(reportFile, report);

            if (jsonReport != null)
                ReportUtil.appendJsonReport(jsonReport, report);
        }
    }

    static final class TaggedSequence {
        final TagTuple tags;
        final SequenceRead rawRead;
        final SequenceRead payloadRead;

        public TaggedSequence(TagTuple tags, SequenceRead rawRead, SequenceRead payloadRead) {
            this.tags = tags;
            this.rawRead = rawRead;
            this.payloadRead = payloadRead;
        }
    }

    static final class TagSearchPlan {
        final ReadSearchPlan plan;
        final List<ReadTagShortcut> tagShortcuts;
        final List<ReadTagShortcut> readShortcuts;
        final List<TagInfo> tagInfos;

        final ParseReport report;

        public TagSearchPlan(ReadSearchPlan plan,
                             List<ReadTagShortcut> tagShortcuts, List<ReadTagShortcut> readShortcuts,
                             List<TagInfo> tagInfos) {
            this.plan = plan;
            this.tagShortcuts = tagShortcuts;
            this.readShortcuts = readShortcuts;
            this.tagInfos = tagInfos;
            this.report = new ParseReport(plan);
        }

        public TaggedSequence parse(SequenceRead read) {
            ReadSearchResult result = plan.search(read);
            report.consume(result);
            ReadSearchHit hit = result.getHit();
            if (hit == null)
                return null;

            TagValue[] tags = tagShortcuts.stream()
                    .map(s -> new SequenceAndQualityTagValue(result.getTagValue(s).getValue()))
                    .toArray(TagValue[]::new);

            SingleRead[] reads = new SingleRead[readShortcuts.size()];
            for (int i = 0; i < reads.length; i++) {
                reads[i] = new SingleReadImpl(
                        read.getId(),
                        result.getTagValue(readShortcuts.get(i)).getValue(),
                        read.numberOfReads() <= i
                                ? read.getRead(0).getDescription()
                                : read.getRead(i).getDescription());
            }

            return new TaggedSequence(new TagTuple(tags), read, SequenceReadUtil.construct(reads));
        }
    }

    public final class ParseInfo {
        private final List<TagInfo> tags;
        private final List<String> readTags;

        public ParseInfo(List<TagInfo> tags, List<String> readTags) {
            Objects.requireNonNull(tags);
            Objects.requireNonNull(readTags);
            this.tags = tags;
            this.readTags = readTags;
        }

        public List<TagInfo> getTags() {
            return tags;
        }

        public List<String> getReadTags() {
            return readTags;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParseInfo parseInfo = (ParseInfo) o;
            return tags.equals(parseInfo.tags) && readTags.equals(parseInfo.readTags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tags, readTags);
        }
    }

    public ParseInfo parseTagsFromSet(Set<String> names) {
        List<TagInfo> tags = new ArrayList<>();
        List<String> readTags = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith("S"))
                tags.add(new TagInfo(Sample, SequenceAndQuality, name, 0));
            else if (name.startsWith("CELL"))
                tags.add(new TagInfo(Cell, SequenceAndQuality, name, 0));
            else if (name.startsWith("UMI") || name.startsWith("MI"))
                tags.add(new TagInfo(Molecule, SequenceAndQuality, name, 0));
            else if (name.matches("R\\d+"))
                readTags.add(name);
            else
                warn("Can't recognize tag type for name \"" + name + "\", this tag will be ignored during analysis.");
        }
        Collections.sort(tags);
        for (int i = 0; i < tags.size(); i++)
            tags.set(i, tags.get(i).withIndex(i));
        tags.stream().map(TagInfo::getType)
                .collect(Collectors.toSet())
                .forEach(tt -> MiXCRMain.lm.reportFeature("mixcr.tag-type", tt.toString()));
        Collections.sort(readTags);
        return new ParseInfo(tags, readTags);
    }
}
