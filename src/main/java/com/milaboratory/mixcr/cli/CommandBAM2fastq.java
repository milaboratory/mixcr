package com.milaboratory.mixcr.cli;

import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.mixcr.bam.BAMReader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(name = "BAM2fastq", hidden = true,
        description = "Converts BAM/SAM file to paired/unpaired fastq files")
public class CommandBAM2fastq extends MiXCRCommand {
    @Option(names = {"-b", "--bam"}, description = "BAM files for conversion.", required = true)
    public String[] bamFiles;

    @Option(names = {"-r1"}, description = "File for first reads.", required = true)
    public String fastq1;

    @Option(names = {"-r2"}, description = "File for second reads.", required = true)
    public String fastq2;

    @Option(names = {"-u"}, description = "File for unpaired reads.", required = true)
    public String fastqUnpaired;

    @Option(names = {"--drop-non-vdj"},
            description = "Drop reads from bam file mapped on human chromosomes except with VDJ region (2, 7, 14, 22)")
    public boolean dropNonVDJ = false;

    @Option(names = {"--keep-wildcards"},
            description = "Keep sequences with wildcards in the output")
    public boolean keepWildcards = false;

    @Override
    protected List<String> getInputFiles() {
        return Arrays.asList(bamFiles);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Stream.of(fastq1, fastq2, fastqUnpaired).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void run0() throws Exception {
        try (BAMReader converter = new BAMReader(bamFiles, dropNonVDJ, !keepWildcards)) {
            SequenceRead read;
            try (PairedFastqWriter wr = new PairedFastqWriter(fastq1, fastq2);
                 SingleFastqWriter swr = new SingleFastqWriter(fastqUnpaired)) {
                while ((read = converter.take()) != null) {
                    if (read instanceof PairedRead) {
                        wr.write((PairedRead) read);
                    } else if (read instanceof SingleRead) {
                        swr.write((SingleRead) read);
                    }
                }
            }

            System.out.println("Your fastq files are ready.");
            System.out.println(converter.getNumberOfProcessedAlignments() + " alignments processed.");
            System.out.println(converter.getNumberOfPairedReads() + " paired reads.");
            System.out.println(converter.getNumberOfUnpairedReads() + " unpaired reads.");
        }
    }
}
