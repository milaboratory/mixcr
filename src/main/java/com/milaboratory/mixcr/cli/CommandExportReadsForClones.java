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
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceWriter;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceWriterWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.util.Concurrency;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@Command(name = "exportReadsForClones",
        sortOptions = true,
        separator = " ",
        description = "Export reads for particular clones from \"clones & alignments\" (*.clna) file. " +
                "Output file name will be transformed into '_R1'/'_R2' pair in case of paired end reads. Use cloneId = -1 to " +
                "export alignments not assigned to any clone (not assembled). If no clone ids are specified (only input " +
                "and output filenames are specified) all reads assigned to clonotypes will be exported.")
public class CommandExportReadsForClones extends ACommandWithOutputMiXCR {
    @Parameters(index = "0", description = "input_file.clna")
    public String in;

    @Parameters(index = "1", description = "[output_file(.fastq[.gz]|fasta)]")
    public String out;

    @Option(names = "--id", description = "[cloneId1 [cloneId2 [cloneId3]]]", arity = "0..*")
    public List<Integer> ids = new ArrayList<>();

    @Override
    public List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    @Option(description = "Create separate files for each clone. File or pair of '_R1'/'_R2' files, with '_clnN' suffix, " +
            "where N is clone index, will be created for each clone index.",
            names = {"-s", "--separate"})
    public boolean separate = false;

    public int[] getCloneIds() {
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void run0() throws Exception {
        try (ClnAReader clna = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4))) {
            VDJCAlignments firstAlignment;
            try (OutputPortCloseable<VDJCAlignments> dummyP = clna.readAllAlignments()) {
                firstAlignment = dummyP.take();
            }

            if (firstAlignment == null)
                return;

            if (firstAlignment.getOriginalReads() == null)
                throwValidationException("Error: original reads were not saved in original .vdjca file: " +
                        "re-run 'align' with '-OsaveOriginalReads=true' option.");

            int[] cid = getCloneIds();
            Supplier<IntStream> cloneIds;
            if (cid.length == 0)
                cloneIds = () -> IntStream.range(0, clna.numberOfClones());
            else
                cloneIds = () -> IntStream.of(cid);

            long totalAlignments = cloneIds.get().mapToLong(clna::numberOfAlignmentsInClone).sum();
            AtomicLong alignmentsWritten = new AtomicLong();
            AtomicBoolean finished = new AtomicBoolean(false);

            SmartProgressReporter.startProgressReport("Writing reads", new CanReportProgress() {
                @Override
                public double getProgress() {
                    return 1.0 * alignmentsWritten.get() / totalAlignments;
                }

                @Override
                public boolean isFinished() {
                    return finished.get();
                }
            });

            boolean paired = firstAlignment.getOriginalReads().get(0).numberOfReads() == 2;

            try (SequenceWriter globalWriter = separate ? null : createWriter(paired, out)) {
                cloneIds.get().forEach(cloneId -> {
                    try (SequenceWriter individualWriter = globalWriter == null ? createWriter(paired, cloneFile(out, cloneId)) : null) {
                        SequenceWriter actualWriter = globalWriter == null ? individualWriter : globalWriter;
                        for (VDJCAlignments alignments : CUtils.it(clna.readAlignmentsOfClone(cloneId))) {
                            for (SequenceRead read : alignments.getOriginalReads())
                                actualWriter.write(read);
                            alignmentsWritten.incrementAndGet();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private static String cloneFile(String fileName, int id) {
        if (fileName.contains(".fast"))
            fileName = fileName.replace(".fast", "_cln" + id + ".fast");
        else fileName += id;
        return fileName;
    }

    private static SequenceWriter createWriter(boolean paired, String fileName) throws IOException {
        String[] split = fileName.split("\\.");
        String ext = split[split.length - 1];
        boolean gz = ext.equals("gz");
        if (gz)
            ext = split[split.length - 2];
        if (ext.equals("fasta")) {
            if (paired)
                throw new IllegalArgumentException("Fasta does not support paired reads.");
            return new FastaSequenceWriterWrapper(fileName);
        } else if (ext.equals("fastq")) {
            if (paired) {
                String fileName1 = fileName.replace(".fastq", "_R1.fastq");
                String fileName2 = fileName.replace(".fastq", "_R2.fastq");
                return new PairedFastqWriter(fileName1, fileName2);
            } else return new SingleFastqWriter(fileName);
        }

        if (paired)
            return new PairedFastqWriter(fileName + "_R1.fastq.gz", fileName + "_R2.fastq.gz");
        else return new SingleFastqWriter(fileName + ".fastq.gz");
    }
}
