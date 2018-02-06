/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import io.repseq.core.VDJCLibraryRegistry;

import java.util.List;

public class VersionInfoAction implements Action {
    final AParameters parameters = new AParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        String inputFile = parameters.getInputFile();
        String i = inputFile.toLowerCase();
        if (i.endsWith(".vdjca.gz") || i.endsWith(".vdjca")) {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(inputFile)) {
                reader.init();
                System.out.println("MagicBytes = " + reader.getMagic());
                System.out.println(reader.getVersionInfo());
            }
        } else if (i.endsWith(".clns.gz") || i.endsWith(".clns")) {
            CloneSet cs = CloneSetIO.read(inputFile);
            System.out.println(cs.getVersionInfo());
        } else if (i.endsWith(".clna")) {
            try (ClnAReader reader = new ClnAReader(inputFile, VDJCLibraryRegistry.createDefaultRegistry())) {
                System.out.println(reader.getVersionInfo());
            }
        } else
            throw new ParameterException("Wrong file type.");
    }

    @Override
    public String command() {
        return "versionInfo";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "Outputs information about MiXCR version which generated the file.")
    private static class AParameters extends ActionParameters {
        @Parameter(description = "binary_file{.vdjca[.gz]|.clns[.gz]|.clna}")
        public List<String> input;

        public String getInputFile() {
            return input.get(0);
        }

        @Override
        public void validate() {
            if (input.size() != 1)
                throw new ParameterException("Wrong number of parameters.");
        }
    }
}
