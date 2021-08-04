package com.milaboratory.mixcr.cli;

import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.mixcr.bam.BAMReader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;

@Command(name = "BAM2fastq", hidden = true,
        description = "Converts BAM/SAM file to paired/unpaired fastq files")
public class CommandBAM2fastq extends ACommandWithOutputMiXCR {
    @Option(names = {"-b", "--bam"}, description = "BAM files for conversion.", required = true)
    private Path[] bamFiles;

    @Option(names = {"-f1", "--fastq1"}, description = "File for first reads.", required = true)
    private File fastq1;

    @Option(names = {"-f2", "--fastq2"}, description = "File for second reads.", required = true)
    private File fastq2;

    @Option(names = {"-fu", "--fastqUnpaired"}, description = "File for unpaired reads.", required = true)
    private File fastqUnpaired;

    @Option(names = {"-v", "--drop-non-vdj"},
            description = "Drop reads from bam file mapped on human chromosomes except with VDJ region (2, 7, 14, 22)")
    public boolean dropNonVDJ = false;

    @Override
    public void run0() throws Exception {
        BAMReader converter = new BAMReader(bamFiles, dropNonVDJ);
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
