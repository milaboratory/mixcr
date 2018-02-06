/*
 * Copyright (c) 2014-2016, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.util.SmartProgressReporter;

import java.io.IOException;
import java.util.List;

public class ActionExportReads implements Action {
    private final AParameters parameters = new AParameters();

    @Override
    @SuppressWarnings("unchecked")
    public void go(ActionHelper helper) throws Exception {
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFile());
             SequenceWriter writer = createWriter()) {
            SmartProgressReporter.startProgressReport("Extracting reads", reader,
                    System.err);

            for (VDJCAlignments alignments : CUtils.it(reader)) {

                List<SequenceRead> reads = alignments.getOriginalReads();
                if (reads == null) {
                    System.err.println("VDJCA file doesn't contain original reads (perform align action with -g / --save-reads option).");
                    return;
                }

                for (SequenceRead read : reads) {
                    if (read.numberOfReads() == 1 && (writer instanceof PairedFastqWriter)) {
                        System.err.println("VDJCA file contains single-end reads, but two output files are specified.");
                        return;
                    }

                    if (read.numberOfReads() == 2 && (writer instanceof SingleFastqWriter)) {
                        System.err.println("VDJCA file contains paired-end reads, but only one / no output file is specified.");
                        return;
                    }

                    writer.write(read);
                }
            }
        }
    }

    public SequenceWriter<?> createWriter() throws IOException {
        String[] outputFiles = parameters.getOutputFiles();
        switch (outputFiles.length) {
            case 0:
                return new SingleFastqWriter(System.out);
            case 1:
                return new SingleFastqWriter(outputFiles[0]);
            case 2:
                return new PairedFastqWriter(outputFiles[0], outputFiles[1]);
        }
        throw new RuntimeException();
    }

    @Override
    public String command() {
        return "exportReads";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "Export original reads from vdjca file.")
    private static final class AParameters extends ActionParameters {
        @Parameter(description = "input.vdjca[.gz] [output_R1.fastq[.gz] [output_R2.fastq[.gz]]]")
        public List<String> parameters;

        public String getInputFile() {
            return parameters.get(0);
        }

        public String[] getOutputFiles() {
            if (parameters.size() == 1)
                return new String[]{};

            if (parameters.size() == 2)
                return new String[]{parameters.get(1)};

            if (parameters.size() == 3)
                return new String[]{parameters.get(1), parameters.get(2)};

            throw new ParameterException("Required parameters missing.");
        }

        @Override
        public void validate() {
            if (parameters.isEmpty() || parameters.size() > 3)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }
    }
}
