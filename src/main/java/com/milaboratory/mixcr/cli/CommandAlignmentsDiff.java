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
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriterI;
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader;
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Command(name = "alignmentsDiff",
        sortOptions = false,
        separator = " ",
        description = "Calculates the difference between two .vdjca files.")
public class CommandAlignmentsDiff extends MiXCRCommand {
    @Parameters(description = "input_file1", index = "0")
    public String in1;

    @Parameters(description = "input_file2", index = "1")
    public String in2;

    @Parameters(description = "report", index = "2", arity = "0..1")
    public String report = null;

    @Option(names = {"-o1", "--only-in-first"}, description = "output for alignments contained only " +
            "in the first .vdjca file")
    public String onlyFirst;
    @Option(names = {"-o2", "--only-in-second"}, description = "output for alignments contained only " +
            "in the second .vdjca file")
    public String onlySecond;
    @Option(names = {"-d1", "--diff-from-first"}, description = "output for alignments from the first file " +
            "that are different from those alignments in the second file")
    public String diff1;
    @Option(names = {"-d2", "--diff-from-second"}, description = "output for alignments from the second file " +
            "that are different from those alignments in the first file")
    public String diff2;
    @Option(names = {"-g", "--gene-feature"}, description = "Specifies a gene feature to compare")
    public String geneFeatureToMatch = "CDR3";
    @Option(names = {"-l", "--top-hits-level"}, description = "Number of top hits to search for a match")
    public int hitsCompareLevel = 1;

    GeneFeature getFeature() {
        return GeneFeature.parse(geneFeatureToMatch);
    }

    @Override
    protected List<String> getInputFiles() {
        return Arrays.asList(in1, in2);
    }

    @Override
    protected List<String> getOutputFiles() {
        return report == null ?
                Collections.emptyList() :
                Collections.singletonList(report);
    }

    @Override
    public void run0() throws Exception {
        try (VDJCAlignmentsReader reader1 = new VDJCAlignmentsReader(in1);
             VDJCAlignmentsReader reader2 = new VDJCAlignmentsReader(in2);
             VDJCAlignmentsWriterI only1 = onlyFirst == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(onlyFirst);
             VDJCAlignmentsWriterI only2 = onlySecond == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(onlySecond);
             VDJCAlignmentsWriterI diff1 = this.diff1 == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(this.diff1);
             VDJCAlignmentsWriterI diff2 = this.diff1 == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(this.diff2);
             PrintStream report = this.report == null ? System.out : new PrintStream(Files.newOutputStream(Paths.get(this.report)))
        ) {
            if (reader1.getNumberOfReads() > reader2.getNumberOfReads())
                SmartProgressReporter.startProgressReport("Analyzing diff", reader1);
            else
                SmartProgressReporter.startProgressReport("Analyzing diff", reader2);

            long same = 0, onlyIn1 = 0, onlyIn2 = 0, diffFeature = 0, justDiff = 0;
            long[] diffHits = new long[GeneType.NUMBER_OF_TYPES];

            only1.header(reader1);
            diff1.header(reader1);
            only2.header(reader2);
            diff2.header(reader2);

            VDJCAlignmentsDifferenceReader diffReader = new VDJCAlignmentsDifferenceReader(reader1, reader2,
                    getFeature(), hitsCompareLevel);
            for (VDJCAlignmentsDifferenceReader.Diff diff : CUtils.it(diffReader)) {
                switch (diff.status) {
                    case AlignmentsAreSame:
                        ++same;
                        break;
                    case AlignmentPresentOnlyInFirst:
                        ++onlyIn1;
                        only1.write(diff.first);
                        break;
                    case AlignmentPresentOnlyInSecond:
                        ++onlyIn2;
                        only2.write(diff.second);
                        break;
                    case AlignmentsAreDifferent:
                        ++justDiff;

                        diff1.write(diff.first);
                        diff2.write(diff.second);

                        if (diff.reason.diffGeneFeature)
                            ++diffFeature;
                        for (Map.Entry<GeneType, Boolean> e : diff.reason.diffHits.entrySet())
                            if (e.getValue())
                                ++diffHits[e.getKey().ordinal()];
                }
            }

            only1.setNumberOfProcessedReads(onlyIn1);
            only2.setNumberOfProcessedReads(onlyIn2);
            diff1.setNumberOfProcessedReads(justDiff);
            diff2.setNumberOfProcessedReads(justDiff);

            report.println("First  file: " + in1);
            report.println("Second file: " + in2);
            report.println("Completely same reads: " + same);
            report.println("Aligned reads present only in the FIRST  file: " + onlyIn1 + " (" + ReportHelper.PERCENT_FORMAT.format(100. * onlyIn1 / reader1.getNumberOfReads()) + ")%");
            report.println("Aligned reads present only in the SECOND file: " + onlyIn2 + " (" + ReportHelper.PERCENT_FORMAT.format(100. * onlyIn2 / reader2.getNumberOfReads()) + ")%");
            report.println("Total number of different reads: " + justDiff);
            report.println("Reads with not same " + geneFeatureToMatch + ": " + diffFeature);

            for (GeneType geneType : GeneType.VDJC_REFERENCE)
                report.println("Reads with not same " + geneType.name() + " hits: " + diffHits[geneType.ordinal()]);
        }
    }
}
