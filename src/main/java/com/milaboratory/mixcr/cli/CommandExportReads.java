/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceWriter;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.util.SmartProgressReporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Command(name = "exportReads",
        sortOptions = true,
        separator = " ",
        description = "Export original reads from vdjca file.")
public class CommandExportReads extends ACommandWithOutputMiXCR {
    @Parameters(description = "input.vdjca [output_R1.fastq[.gz] [output_R2.fastq[.gz]]]", arity = "1..3")
    public List<String> inOut;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(inOut.get(0));
    }

    @Override
    public List<String> getOutputFiles() {
        if (inOut.size() == 1)
            return Collections.emptyList();

        if (inOut.size() == 2)
            return Collections.singletonList(inOut.get(1));

        if (inOut.size() == 3)
            return Arrays.asList(inOut.get(1), inOut.get(2));

        throwValidationException("Required parameters missing.");
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run0() throws Exception {
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(getInputFiles().get(0));
             SequenceWriter writer = createWriter()) {
            SmartProgressReporter.startProgressReport("Extracting reads", reader, System.err);

            for (VDJCAlignments alignments : CUtils.it(reader)) {
                List<SequenceRead> reads = alignments.getOriginalReads();
                if (reads == null)
                    throwExecutionException("VDJCA file doesn't contain original reads (perform align action with -g / --save-reads option).");

                for (SequenceRead read : reads) {
                    if (read.numberOfReads() == 1 && (writer instanceof PairedFastqWriter))
                        throwExecutionException("VDJCA file contains single-end reads, but two output files are specified.");

                    if (read.numberOfReads() == 2 && (writer instanceof SingleFastqWriter))
                        throwExecutionException("VDJCA file contains paired-end reads, but only one / no output file is specified.");

                    writer.write(read);
                }
            }
        }
    }

    public SequenceWriter<?> createWriter() throws IOException {
        List<String> outputFiles = getOutputFiles();
        switch (outputFiles.size()) {
            case 0:
                return new SingleFastqWriter(System.out);
            case 1:
                return new SingleFastqWriter(outputFiles.get(0));
            case 2:
                return new PairedFastqWriter(outputFiles.get(0), outputFiles.get(1));
        }
        throw new RuntimeException();
    }
}
