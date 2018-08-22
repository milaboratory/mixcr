/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceWriter;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceWriterWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionExportCloneReads implements Action {
    private final ExtractCloneParameters parameters = new ExtractCloneParameters();

    @Override
    public String command() {
        return "exportReadsForClones";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Override
    public void go(ActionHelper helper) throws Exception {
        try (ClnAReader clna = new ClnAReader(parameters.getInputFileName(), VDJCLibraryRegistry.getDefault())) {
            VDJCAlignments firstAlignment = clna.readAllAlignments().take();
            if (firstAlignment == null)
                return;

            if (firstAlignment.getOriginalReads() == null)
                throw new ParameterException("Error: original reads were not saved in the .vdjca file: " +
                        "re-run align with '-g' option.");

            int[] cid = parameters.getCloneIds();
            Supplier<IntStream> cloneIds;
            if (cid == null)
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
            boolean separate = parameters.doSeparate();

            SequenceWriter globalWriter = separate ? null : createWriter(paired, parameters.getOutputFileName());

            cloneIds.get().forEach(cloneId -> {
                try (SequenceWriter individualWriter = globalWriter == null ? createWriter(paired, cloneFile(parameters.getOutputFileName(), cloneId)) : null) {
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

    @Parameters(commandDescription = "Export reads for particular clones from \"clones & alignments\" file. " +
            "Output file name will be modified to '_R1'/'_R2' in case of paired end reads. Use cloneId = -1 to " +
            "export alignments not assigned to any clone. If no clone ids are specified (only input and output " +
            "filenames are specified) all reads assigned to clonotypes will be exported.")
    public static final class ExtractCloneParameters extends ActionParameters {
        @Parameter(description = "clonesAndAlignments.clna [cloneId1 [cloneId2 [cloneId3]]] ... output[.fastq[.gz]|.fasta]")
        public List<String> parameters;

        @Parameter(description = "Create separate files for each clone. _clnN, where N is clone index will be added to the file name.",
                names = {"-s", "--separate"})
        public boolean separate = false;

        public boolean doSeparate() {
            return separate;
        }

        public String getInputFileName() {
            return parameters.get(0);
        }

        public int[] getCloneIds() {
            if (parameters.size() == 2)
                return null;
            int[] cloneIds = new int[parameters.size() - 2];
            for (int i = 1; i < parameters.size() - 1; ++i)
                cloneIds[i - 1] = Integer.valueOf(parameters.get(i));
            return cloneIds;
        }

        public String getOutputFileName() {
            return parameters.get(parameters.size() - 1);
        }

        @Override
        public void validate() {
            if (parameters.size() < 2)
                throw new ParameterException("Required parameters missing.");
            super.validate();
        }
    }
}
