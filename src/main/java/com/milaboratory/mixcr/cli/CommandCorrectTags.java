package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.Processor;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.CountingOutputPort;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
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
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.sorting.HashSorter;
import gnu.trove.list.array.TIntArrayList;
import org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

import static com.milaboratory.mixcr.cli.CommandCorrectTags.CORRECT_TAGS_COMMAND_NAME;
import static picocli.CommandLine.Option;

@CommandLine.Command(name = CORRECT_TAGS_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Builds alignments with V,D,J and C genes for input sequencing reads.")
public class CommandCorrectTags extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String CORRECT_TAGS_COMMAND_NAME = "correctTags";

    @Option(description = "This parameter determines how thorough the procedure should eliminate variants looking like errors. " +
            "Smaller value leave less erroneous variants at the cost of accidentally correcting true variants. " +
            "This value approximates the fraction of erroneous variants the algorithm will miss (type II errors).",
            names = {"-p", "--power"})
    public double power = 1E-3;

    @Option(description = "Expected background non-sequencing-related substitution rate",
            names = {"-s", "--substitution-rate"})
    public double backgroundSubstitutionRate = 1E-3;

    @Option(description = "Expected background non-sequencing-related indel rate",
            names = {"-i", "--indel-rate"})
    public double backgroundIndelRate = 1E-5;

    @Option(description = "Minimal quality score for the tag. " +
            "Tags having positions with lower quality score will be discarded, if not corrected.",
            names = {"-q", "--min-quality"})
    public int minQuality = 12;

    @Option(description = "Maximal number of substitutions to search for.",
            names = {"--max-substitutions"})
    public int maxSubstitutions = 2;

    @Option(description = "Maximal number of indels to search for.",
            names = {"--max-indels"})
    public int maxIndels = 1;

    @Option(description = "Use system temp folder for temporary files.",
            names = {"--use-system-temp"})
    public boolean useSystemTemp = false;

    @Option(description = "Memory budget",
            names = {"--memory-budget"})
    public long memoryBudget = 1L << 32; // 4 GB

    @Option(description = CommonDescriptions.REPORT,
            names = {"-r", "--report"})
    public String reportFile;

    private Path tempFolder() {
        try {
            File tempFolder;
            if (useSystemTemp)
                tempFolder = Paths.get(System.getProperty("java.io.tmpdir"))
                        .resolve(Paths.get(out).getFileName().getFileName() + "." +
                                Long.toString(System.nanoTime(), 36))
                        .toFile();
            else
                tempFolder = new File(out + ".tmp");
            if (tempFolder.exists())
                FileUtils.deleteDirectory(tempFolder);
            Files.createDirectory(tempFolder.toPath());
            TempFileManager.register(tempFolder);
            return tempFolder.toPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    TagCorrectorParameters getParameters() {
        return new TagCorrectorParameters(
                power, backgroundSubstitutionRate, backgroundIndelRate,
                minQuality, maxSubstitutions, maxIndels
        );
    }

    @Override
    public ActionConfiguration<?> getConfiguration() {
        return new CorrectTagsConfiguration(getParameters());
    }

    @Override
    public void run1() throws Exception {
        CorrectionNode correctioResult;
        CorrectionReport report;
        int[] targetTagIndices;
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in)) {
            TagsInfo tagsInfo = reader.getTagsInfo();

            List<String> tagNames = new ArrayList<>();
            TIntArrayList indicesBuilder = new TIntArrayList();
            for (int i = 0; i < tagsInfo.tags.length; i++) {
                TagInfo tag = tagsInfo.tags[i];
                assert i == tag.getIndex(); // just in case
                if (tag.getValueType() == TagValueType.SequenceAndQuality)
                    indicesBuilder.add(i);
                tagNames.add(tag.getName());
            }
            targetTagIndices = indicesBuilder.toArray();

            System.out.println("Correction will be applied to the following tags: " + String.join(", ", tagNames));

            TagCorrector corrector = new TagCorrector(getParameters(),
                    tempFolder(), "",
                    memoryBudget,
                    4, 4);
            SmartProgressReporter.startProgressReport(corrector);

            // Extractor of tag information from the alignments for the tag corrector
            Processor<VDJCAlignments, NSequenceWithQuality[]> mapper = input -> {
                if (input.getTagCounter().size() != 1)
                    throwExecutionException("This procedure don't support aggregated tags. " +
                            "Please run tag correction for *.vdjca files produced by 'align'.");
                TagTuple tagTuple = input.getTagCounter().keys().iterator().next();
                NSequenceWithQuality[] tags = new NSequenceWithQuality[targetTagIndices.length];
                for (int i = 0; i < targetTagIndices.length; i++)
                    tags[i] = ((SequenceAndQualityTagValue) tagTuple.tags[targetTagIndices[i]]).data;
                return tags;
            };
            OutputPort<NSequenceWithQuality[]> cInput = CUtils.wrap(reader, mapper);
            correctioResult = corrector.correct(cInput, tagNames, reader);
            report = corrector.getReport();
        }

        ToIntFunction<VDJCAlignments> hashFunction = al -> {
            TagValue[] tags = al.getTagCounter().keys().iterator().next().tags;
            //noinspection UnstableApiUsage
            Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
            for (int i : targetTagIndices)
                //noinspection UnstableApiUsage
                hasher.putInt(((SequenceAndQualityTagValue) tags[i]).data.getSequence().hashCode());
            //noinspection UnstableApiUsage
            return hasher.hash().hashCode();
        };

        Comparator<VDJCAlignments> comparator = (al1, al2) -> {
            TagValue[] tags1 = al1.getTagCounter().keys().iterator().next().tags;
            TagValue[] tags2 = al2.getTagCounter().keys().iterator().next().tags;
            int c;
            for (int i : targetTagIndices)
                if ((c = ((SequenceAndQualityTagValue) tags1[i]).data.getSequence().compareTo(
                        ((SequenceAndQualityTagValue) tags2[i]).data.getSequence())) != 0)
                    return c;
            return 0;
        };

        try (
                VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in);
                VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)
        ) {
            PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();
            IOUtil.registerGeneReferences(stateBuilder, reader.getUsedGenes(), reader.getParameters());

            HashSorter<VDJCAlignments> hashSorter = new HashSorter<>(
                    VDJCAlignments.class,
                    hashFunction, comparator,
                    4, tempFolder().resolve("hashsorter"),
                    4, 4,
                    stateBuilder.getOState(), stateBuilder.getIState(),
                    memoryBudget, 10000
            );

            SmartProgressReporter.startProgressReport("Applying correction & sorting alignments", reader);

            Processor<VDJCAlignments, VDJCAlignments> mapper = al -> {
                TagTuple tt = al.getTagCounter().keys().iterator().next();
                TagValue[] newTags = tt.tags.clone();
                CorrectionNode cn = correctioResult;
                for (int i : targetTagIndices) {
                    NucleotideSequence current = ((SequenceAndQualityTagValue) newTags[i]).data.getSequence();
                    cn = cn.getNextLevel().get(current);
                    if (cn == null) {
                        report.setFilteredRecords(report.getFilteredRecords() + 1);
                        return al.setTagCounter(null); // will be filtered right before hash sorter
                    }
                    newTags[i] = new SequenceAndQualityTagValue(cn.getCorrectValue());
                }
                return al.setTagCounter(new TagCounter(new TagTuple(newTags)));
            };

            // Creating output port with corrected and filtered tags
            OutputPort<VDJCAlignments> hsInput = new FilteringPort<>(
                    CUtils.wrap(reader, mapper), al -> al.getTagCounter() != null);

            // Running hash sorter
            CountingOutputPort<VDJCAlignments> sorted = new CountingOutputPort<>(hashSorter.port(hsInput));

            SmartProgressReporter.startProgressReport("Writing result",
                    SmartProgressReporter.extractProgress(sorted, reader.getNumberOfReads()));

            // Initializing and writing results to the output file
            writer.header(reader, getFullPipelineConfiguration(), reader.getTagsInfo());
            for (VDJCAlignments al : CUtils.it(sorted))
                writer.write(al);
        }

        report.writeReport(ReportHelper.STDOUT);
    }

    public static final class CorrectTagsConfiguration implements ActionConfiguration<CorrectTagsConfiguration> {
        final TagCorrectorParameters parameters;

        @JsonCreator
        public CorrectTagsConfiguration(@JsonProperty("parameters") TagCorrectorParameters parameters) {
            this.parameters = parameters;
        }

        @Override
        public String actionName() {
            return CORRECT_TAGS_COMMAND_NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CorrectTagsConfiguration that = (CorrectTagsConfiguration) o;
            return parameters.equals(that.parameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parameters);
        }
    }
}
