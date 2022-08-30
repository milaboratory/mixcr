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
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory;
import com.milaboratory.mixcr.export.FieldExtractor;
import com.milaboratory.mixcr.export.OutputMode;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapDataset;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil;
import com.milaboratory.mixcr.postanalysis.util.OverlapBrowser;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.Chains.NamedChains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Command(
        separator = " ",
        description = "Build cloneset overlap and export into tab delimited file.",
        sortOptions = false
)
public class CommandExportOverlap extends MiXCRCommand {
    @Parameters(description = "cloneset.{clns|clna}... output.tsv")
    public List<String> inOut;

    @Option(description = "Chains to export",
            names = "--chains")
    public List<String> chains = null;

    @Option(description = "Filter out-of-frame sequences and clonotypes with stop-codons",
            names = {"--only-productive"})
    public boolean onlyProductive = false;

    @Option(description = "Overlap criteria. Default CDR3|AA|V|J",
            names = {"--criteria"})
    public String overlapCriteria = "CDR3|AA|V|J";

    /**
     * auto-generated opts (exporters) injected manually
     */
    private CommandSpec spec;

    @Override
    public List<String> getInputFiles() {
        return inOut.subList(0, inOut.size() - 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(inOut.get(inOut.size() - 1));
    }

    public Path getOut(NamedChains chains) {
        Path out = Paths.get(inOut.get(inOut.size() - 1)).toAbsolutePath();
        if (chains == Chains.ALL_NAMED)
            return out;
        String fName = out.getFileName().toString();
        if (fName.endsWith(".tsv"))
            fName = fName.replace("tsv", "") + chains.name + ".tsv";
        else
            fName = fName + "_" + chains.name;
        return out.getParent().resolve(fName);
    }

    @Override
    public void run0() throws Exception {
        List<String> samples = getInputFiles();
        List<NamedChains> chains = this.chains == null
                ? Collections.singletonList(Chains.ALL_NAMED)
                : this.chains.stream().map(Chains::getNamedChains).collect(Collectors.toList());

        OverlapUtil.OverlapCriteria criteria = OverlapUtil.parseCriteria(this.overlapCriteria);
        List<OverlapFieldExtractor> extractors = new ArrayList<>();

        extractors.add(new ExtractorUnique(
                new FieldExtractor<Clone>() {
                    @NotNull
                    @Override
                    public String getHeader() {
                        return (criteria.isAA ? "aaSeq" : "nSeq") + GeneFeature.encode(criteria.feature);
                    }

                    @NotNull
                    @Override
                    public String extractValue(@NotNull Clone object) {
                        return criteria.isAA
                                ? object.getAAFeature(criteria.feature).toString()
                                : object.getNFeature(criteria.feature).toString();
                    }
                }
        ));
        if (criteria.withV)
            extractors.add(new ExtractorUnique(
                    new FieldExtractor<Clone>() {
                        @NotNull
                        @Override
                        public String getHeader() {
                            return "vGene";
                        }

                        @NotNull
                        @Override
                        public String extractValue(@NotNull Clone object) {
                            return object.getBestHit(GeneType.Variable).getGene().getName();
                        }
                    }
            ));
        if (criteria.withJ)
            extractors.add(new ExtractorUnique(
                    new FieldExtractor<Clone>() {
                        @NotNull
                        @Override
                        public String getHeader() {
                            return "jGene";
                        }

                        @NotNull
                        @Override
                        public String extractValue(@NotNull Clone object) {
                            return object.getBestHit(GeneType.Joining).getGene().getName();
                        }
                    }
            ));

        extractors.add(new NumberOfSamples());
        extractors.add(new TotalCount());
        extractors.add(new TotalFraction());

        List<FieldExtractor<? super Clone>> fieldExtractors;
        try (CloneReader cReader = CloneSetIO.mkReader(Paths.get(samples.get(0)), VDJCLibraryRegistry.getDefault())) {
            fieldExtractors = CloneFieldsExtractorsFactory.INSTANCE
                    .parseSpec(spec.commandLine().getParseResult())
                    .stream()
                    .flatMap(f -> CloneFieldsExtractorsFactory.INSTANCE.extract(f, cReader, OutputMode.ScriptingFriendly).stream())
                    .collect(Collectors.toList());
        }

        for (FieldExtractor<? super Clone> fe : fieldExtractors)
            extractors.add(new ExtractorPerSample(fe));

        Map<NamedChains, InfoWriter> writers = new HashMap<>();
        for (NamedChains chain : chains) {
            InfoWriter writer = new InfoWriter(getOut(chain), samples, extractors);
            writer.writeHeader();
            writers.put(chain, writer);
        }

        OverlapBrowser overlapBrowser = new OverlapBrowser(chains, onlyProductive);
        SmartProgressReporter.startProgressReport(overlapBrowser);
        Map<NamedChains, double[]> counts = overlapBrowser.computeCounts(samples);

        OverlapDataset<Clone> overlap = OverlapUtil.overlap(samples, __ -> true, criteria.ordering());
        try (OutputPortWithProgress<OverlapGroup<Clone>> port = overlap.mkElementsPort()) {
            for (Map<NamedChains, OverlapGroup<Clone>> row : CUtils.it(overlapBrowser.overlap(counts, port))) {
                for (Map.Entry<NamedChains, OverlapGroup<Clone>> e : row.entrySet()) {
                    writers.get(e.getKey()).writeRow(e.getValue());
                }
            }
        }
        writers.forEach((___, w) -> w.close());
    }

    private static final class InfoWriter implements AutoCloseable {
        final PrintWriter writer;
        final List<String> samples;
        final List<String> samplesNames;
        final List<OverlapFieldExtractor> extractors;

        public InfoWriter(Path out, List<String> samples,
                          List<OverlapFieldExtractor> extractors) {
            try {
                this.writer = new PrintWriter(new FileWriter(out.toFile()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.samples = samples;
            this.samplesNames = samples.stream().map(InfoWriter::removeExt).collect(Collectors.toList());
            ;
            this.extractors = extractors;
        }

        static String removeExt(String sampleName) {
            return sampleName
                    .replace(".clns", "")
                    .replace(".clna", "");
        }

        void writeHeader() {
            writer.println(extractors.stream().flatMap(e -> e.header(samplesNames).stream()).collect(Collectors.joining("\t")));
        }

        void writeRow(OverlapGroup<Clone> row) {
            writer.println(extractors.stream().flatMap(e -> e.values(row).stream()).collect(Collectors.joining("\t")));
        }

        @Override
        public void close() {
            writer.close();
        }
    }

    private interface OverlapFieldExtractor {
        List<String> header(List<String> samples);

        List<String> values(OverlapGroup<Clone> row);
    }

    private static final class TotalCount implements OverlapFieldExtractor {
        @Override
        public List<String> header(List<String> samples) {
            return samples.stream().map(s -> s + "_countAggregated").collect(Collectors.toList());
        }

        @Override
        public List<String> values(OverlapGroup<Clone> row) {
            return row.stream().map(l -> Double.toString(l.stream().mapToDouble(Clone::getCount).sum()))
                    .collect(Collectors.toList());
        }
    }

    private static final class TotalFraction implements OverlapFieldExtractor {
        @Override
        public List<String> header(List<String> samples) {
            return samples.stream().map(s -> s + "_fractionAggregated").collect(Collectors.toList());
        }

        @Override
        public List<String> values(OverlapGroup<Clone> row) {
            return row.stream().map(l -> Double.toString(l.stream().mapToDouble(Clone::getFraction).sum()))
                    .collect(Collectors.toList());
        }
    }

    private static final class NumberOfSamples implements OverlapFieldExtractor {
        @Override
        public List<String> header(List<String> samples) {
            return Collections.singletonList("nSamples");
        }

        @Override
        public List<String> values(OverlapGroup<Clone> row) {
            return Collections.singletonList("" + row.stream().filter(l -> l.size() > 0).count());
        }
    }

    private static final class ExtractorUnique implements OverlapFieldExtractor {
        final FieldExtractor<Clone> extractor;

        public ExtractorUnique(FieldExtractor<Clone> extractor) {
            this.extractor = extractor;
        }

        @Override
        public List<String> header(List<String> samples) {
            return Collections.singletonList(extractor.getHeader());
        }

        @Override
        public List<String> values(OverlapGroup<Clone> row) {
            return Collections.singletonList(extractor.extractValue(row.stream().filter(l -> l.size() > 0).findAny().get().get(0)));
        }
    }

    private static final class ExtractorPerSample implements OverlapFieldExtractor {
        final FieldExtractor<? super Clone> extractor;

        public ExtractorPerSample(FieldExtractor<? super Clone> extractor) {
            this.extractor = extractor;
        }

        @Override
        public List<String> header(List<String> samples) {
            return samples.stream().map(s -> s + "_" + extractor.getHeader()).collect(Collectors.toList());
        }

        @Override
        public List<String> values(OverlapGroup<Clone> row) {
            return row.stream().map(s ->
                    s.stream().map(extractor::extractValue).collect(Collectors.joining(","))
            ).collect(Collectors.toList());
        }

    }

    public static CommandSpec mkSpec() {
        CommandExportOverlap export = new CommandExportOverlap();
        CommandSpec spec = CommandSpec.forAnnotatedObject(export);
        export.spec = spec; // inject spec manually
        CloneFieldsExtractorsFactory.INSTANCE.addOptionsToSpec(spec, false);
        return spec;
    }
}
