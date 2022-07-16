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
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.ShortSequenceSet;
import com.milaboratory.mitool.pattern.LibraryStructurePresetCollection;
import com.milaboratory.mitool.pattern.LibraryStructurePresetCollection.LibraryStructurePreset;
import com.milaboratory.mitool.pattern.SequenceSetCollection;
import com.milaboratory.mitool.refinement.CorrectionNode;
import com.milaboratory.mitool.refinement.CorrectionReport;
import com.milaboratory.mitool.refinement.TagCorrector;
import com.milaboratory.mitool.refinement.TagCorrectorParameters;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.tag.*;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.*;
import com.milaboratory.util.sorting.HashSorter;
import gnu.trove.list.array.TIntArrayList;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import static com.milaboratory.mitool.refinement.TagCorrectorParameters.*;
import static com.milaboratory.mixcr.cli.CommandCorrectAndSortTags.CORRECT_AND_SORT_TAGS_COMMAND_NAME;
import static com.milaboratory.mixcr.cli.Util.default3;
import static java.util.stream.Collectors.toMap;
import static picocli.CommandLine.Option;

@CommandLine.Command(name = CORRECT_AND_SORT_TAGS_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Applies error correction algorithm for tag sequences and sorts resulting file by tags.")
public class CommandCorrectAndSortTags extends MiXCRCommand {
    static final String CORRECT_AND_SORT_TAGS_COMMAND_NAME = "correctAndSortTags";

    @Parameters(description = "alignments.vdjca", index = "0")
    public String in;

    @Parameters(description = "alignments.corrected.vdjca", index = "1")
    public String out;

    @Option(description = "Don't correct barcodes, only sort alignments by tags.",
            names = {"--dont-correct"})
    public boolean noCorrect = false;

    @Option(description = "This parameter determines how thorough the procedure should eliminate variants looking like errors. " +
            "Smaller value leave less erroneous variants at the cost of accidentally correcting true variants. " +
            "This value approximates the fraction of erroneous variants the algorithm will miss (type II errors). " +
            "(default " + DEFAULT_CORRECTION_POWER + " or from preset specified on align step)",
            names = {"-p", "--power"})
    public Double power = null;

    @Option(description = "Expected background non-sequencing-related substitution rate (default " +
            DEFAULT_BACKGROUND_SUBSTITUTION_RATE + " or from preset specified on align step)",
            names = {"-s", "--substitution-rate"})
    public Double backgroundSubstitutionRate = null;

    @Option(description = "Expected background non-sequencing-related indel rate (default " +
            DEFAULT_BACKGROUND_INDEL_RATE + " or from preset specified on align step)",
            names = {"-i", "--indel-rate"})
    public Double backgroundIndelRate = null;

    @Option(description = "Minimal quality score for the tag. " +
            "Tags having positions with lower quality score will be discarded, if not corrected (default " +
            DEFAULT_MIN_QUALITY + " or from preset specified on align step)",
            names = {"-q", "--min-quality"})
    public Integer minQuality = null;

    @Option(description = "Maximal number of substitutions to search for (default " +
            DEFAULT_MAX_SUBSTITUTIONS + " or from preset specified on align step)",
            names = {"--max-substitutions"})
    public Integer maxSubstitutions = null;

    @Option(description = "Maximal number of indels to search for (default " +
            DEFAULT_MAX_INDELS + " or from preset specified on align step)",
            names = {"--max-indels"})
    public Integer maxIndels = null;

    @Option(description = "Maximal number of substitutions and indels combined to search for (default " +
            DEFAULT_MAX_TOTAL_ERROR + " or from preset specified on align step)",
            names = {"--max-errors"})
    public Integer maxTotalErrors = null;

    @Option(names = {"-w", "--whitelist"}, description = "Use whitelist-driven correction for one of the tags. Usage: " +
            "--whitelist CELL=preset:737K-august-2016 or -w UMI=file:my_umi_whitelist.txt. If not specified mixcr will set " +
            "correct whitelists if --tag-preset was used on align step.")
    public Map<String, String> whitelists = new HashMap<>();

    @Option(description = "Use system temp folder for temporary files.",
            names = {"--use-system-temp"})
    public boolean useSystemTemp = false;

    @Option(description = "Memory budget",
            names = {"--memory-budget"})
    public long memoryBudget = 1L << 32; // 4 GB

    @Option(description = CommonDescriptions.REPORT,
            names = {"-r", "--report"})
    public String reportFile;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    private void setPresetName(String name) {
        if (name == null)
            return;
        preset = LibraryStructurePresetCollection.INSTANCE.getPresetByName(name);
    }

    private LibraryStructurePreset preset = null;

    private Map<String, String> getWhitelists() {
        if (whitelists == null || whitelists.isEmpty())
            return preset == null
                    ? Collections.EMPTY_MAP
                    : preset.getWhitelists().entrySet()
                    .stream()
                    .collect(toMap(Map.Entry::getKey, e -> "preset:" + e.getValue()));
        else
            return whitelists;
    }

    public <T> T defaultHelper(T optionValue, Function<TagCorrectorParameters, T> presetExtractor2, T defaultValue) {
        return default3(optionValue, preset,
                LibraryStructurePreset::getTagCorrectionParameters,
                presetExtractor2, defaultValue);
    }

    TagCorrectorParameters getParameters() {
        return new TagCorrectorParameters(
                defaultHelper(power,
                        TagCorrectorParameters::getCorrectionPower,
                        DEFAULT_CORRECTION_POWER),
                defaultHelper(backgroundSubstitutionRate,
                        TagCorrectorParameters::getBackgroundSubstitutionRate,
                        DEFAULT_BACKGROUND_SUBSTITUTION_RATE),
                defaultHelper(backgroundIndelRate,
                        TagCorrectorParameters::getBackgroundIndelRate,
                        DEFAULT_BACKGROUND_INDEL_RATE),
                defaultHelper(minQuality,
                        TagCorrectorParameters::getMinQuality,
                        DEFAULT_MIN_QUALITY),
                defaultHelper(maxSubstitutions,
                        TagCorrectorParameters::getMaxSubstitutions,
                        DEFAULT_MAX_SUBSTITUTIONS),
                defaultHelper(maxIndels,
                        TagCorrectorParameters::getMaxIndels,
                        DEFAULT_MAX_INDELS),
                defaultHelper(maxTotalErrors,
                        TagCorrectorParameters::getMaxTotalError,
                        DEFAULT_MAX_TOTAL_ERROR)
        );
    }

    @Override
    public void run0() throws Exception {
        TempFileDest tempDest = TempFileManager.smartTempDestination(out, "", useSystemTemp);

        final CorrectionNode correctionResult;
        final CorrectionReport report;
        final int[] targetTagIndices;
        final List<String> tagNames;
        try (VDJCAlignmentsReader mainReader = new VDJCAlignmentsReader(in)) {
            setPresetName(mainReader.getInfo().getTagPreset());
            TagsInfo tagsInfo = mainReader.getTagsInfo();

            tagNames = new ArrayList<>();
            TIntArrayList indicesBuilder = new TIntArrayList();
            for (int ti = 0; ti < tagsInfo.size(); ti++) {
                TagInfo tag = tagsInfo.get(ti);
                assert ti == tag.getIndex(); // just in case
                if (tag.getValueType() == TagValueType.SequenceAndQuality)
                    indicesBuilder.add(ti);
                tagNames.add(tag.getName());
            }
            targetTagIndices = indicesBuilder.toArray();

            System.out.println((noCorrect ? "Sorting" : "Correction") +
                    " will be applied to the following tags: " + String.join(", ", tagNames));

            if (!noCorrect) {
                TagCorrector corrector = new TagCorrector(getParameters(),
                        tempDest.addSuffix("tags"),
                        memoryBudget,
                        4, 4);
                SmartProgressReporter.startProgressReport(corrector);

                // Extractor of tag information from the alignments for the tag corrector
                Processor<VDJCAlignments, NSequenceWithQuality[]> mapper = input -> {
                    if (input.getTagCount().size() != 1)
                        throwExecutionException("This procedure don't support aggregated tags. " +
                                "Please run tag correction for *.vdjca files produced by 'align'.");
                    TagTuple tagTuple = input.getTagCount().tuples().iterator().next();
                    NSequenceWithQuality[] tags = new NSequenceWithQuality[targetTagIndices.length];
                    for (int i = 0; i < targetTagIndices.length; i++)
                        tags[i] = ((SequenceAndQualityTagValue) tagTuple.get(targetTagIndices[i])).data;
                    return tags;
                };
                OutputPort<NSequenceWithQuality[]> cInput = CUtils.wrap(mainReader, mapper);

                // Running correction
                Map<String, String> whitelistsOptions = getWhitelists();
                Map<Integer, ShortSequenceSet> whitelists = new HashMap<>();
                for (int i = 0; i < tagNames.size(); i++) {
                    String t = whitelistsOptions.get(tagNames.get(i));
                    if (t != null) {
                        System.out.println("The following whitelist will be used for " + tagNames.get(i) + ": " + t);
                        whitelists.put(i, SequenceSetCollection.INSTANCE.loadSequenceSetByAddress(t));
                    }
                }

                correctionResult = corrector.correct(cInput, tagNames, whitelists, mainReader);
                report = corrector.getReport();
            } else {
                correctionResult = null;
                report = null;
            }

            try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {
                VDJCAlignmentsReader.SecondaryReader secondaryReader = mainReader.readAlignments();

                PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();
                IOUtil.registerGeneReferences(stateBuilder, mainReader.getUsedGenes(), mainReader.getParameters());

                IntFunction<HashSorter<VDJCAlignments>> hashSorterFactory = tagIdx -> {
                    SortingStep sortingStep = new SortingStep(tagIdx);
                    return new HashSorter<>(
                            VDJCAlignments.class,
                            sortingStep.getHashFunction(), sortingStep.getComparator(),
                            4, tempDest.addSuffix("hashsorter." + tagIdx),
                            4, 4,
                            stateBuilder.getOState(), stateBuilder.getIState(),
                            memoryBudget, 10000
                    );
                };

                HashSorter<VDJCAlignments> initialHashSorter = hashSorterFactory.apply(targetTagIndices[targetTagIndices.length - 1]);

                SmartProgressReporter.startProgressReport(!noCorrect
                                ? "Applying correction & sorting alignments by " + tagNames.get(targetTagIndices.length - 1)
                                : "Sorting alignments by " + tagNames.get(targetTagIndices.length - 1),
                        secondaryReader);

                Processor<VDJCAlignments, VDJCAlignments> mapper = !noCorrect
                        ?
                        al -> {
                            TagValue[] newTags = al.getTagCount().getSingletonTuple().asArray();
                            CorrectionNode cn = correctionResult;
                            for (int i : targetTagIndices) {
                                NucleotideSequence current = ((SequenceAndQualityTagValue) newTags[i]).data.getSequence();
                                cn = cn.getNextLevel().get(current);
                                if (cn == null) {
                                    report.setFilteredRecords(report.getFilteredRecords() + 1);
                                    return al.setTagCount(null); // will be filtered right before hash sorter
                                }
                                newTags[i] = new SequenceAndQualityTagValue(cn.getCorrectValue());
                            }
                            return al.setTagCount(new TagCount(new TagTuple(newTags)));
                        }
                        : al -> al;

                // Creating output port with corrected and filtered tags
                OutputPort<VDJCAlignments> hsInput = new FilteringPort<>(
                        CUtils.wrap(secondaryReader, mapper), al -> al.getTagCount() != null);

                // Running initial hash sorter
                CountingOutputPort<VDJCAlignments> sorted = new CountingOutputPort<>(initialHashSorter.port(hsInput));

                // Sorting by other tags
                for (int tagIdxIdx = targetTagIndices.length - 2; tagIdxIdx >= 0; tagIdxIdx--) {
                    SmartProgressReporter.startProgressReport("Sorting alignments by " + tagNames.get(tagIdxIdx),
                            SmartProgressReporter.extractProgress(sorted, mainReader.getNumberOfAlignments()));
                    sorted = new CountingOutputPort<>(hashSorterFactory.apply(targetTagIndices[tagIdxIdx]).port(sorted));
                }

                SmartProgressReporter.startProgressReport("Writing result",
                        SmartProgressReporter.extractProgress(sorted, mainReader.getNumberOfAlignments()));

                // Initializing and writing results to the output file
                writer.header(mainReader.getInfo().updateTagInfo(ti -> ti.setSorted(ti.size())),
                        mainReader.getUsedGenes());
                writer.setNumberOfProcessedReads(mainReader.getNumberOfReads());
                for (VDJCAlignments al : CUtils.it(sorted))
                    writer.write(al);

                writer.writeFooter(mainReader.reports(), null); // TODO add correction report
            }
        }

        if (report != null) {
            report.writeReport(ReportHelper.STDOUT);
            if (reportFile != null)
                ReportUtil.appendReport(reportFile, report);
        }
    }

    private static final class SortingStep {
        final int tagIdx;

        public SortingStep(int tagIdx) {
            this.tagIdx = tagIdx;
        }

        public ToIntFunction<VDJCAlignments> getHashFunction() {
            return al -> {
                TagTuple tagTuple = al.getTagCount().getSingletonTuple();
                return ((SequenceAndQualityTagValue) tagTuple.get(tagIdx)).data.getSequence().hashCode();
            };
        }

        public Comparator<VDJCAlignments> getComparator() {
            return (al1, al2) -> {
                TagTuple tagTuple1 = al1.getTagCount().getSingletonTuple();
                TagTuple tagTuple2 = al2.getTagCount().getSingletonTuple();
                return ((SequenceAndQualityTagValue) tagTuple1.get(tagIdx)).data.getSequence().compareTo(
                        ((SequenceAndQualityTagValue) tagTuple2.get(tagIdx)).data.getSequence());
            };
        }
    }
}
