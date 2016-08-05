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

import cc.redberry.pipe.CUtils;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.HiddenAction;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.util.PrintStreamTableAdapter;
import com.milaboratory.util.SmartProgressReporter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@HiddenAction
public class ActionInfo implements Action {
    final InfoParameters parameters = new InfoParameters();
    final PrintStream stream = System.out;
    final PrintStreamTableAdapter tableAdapter = new PrintStreamTableAdapter(stream);

    @Override
    public void go(ActionHelper helper) throws Exception {
        if (!parameters.isTableView())
            throw new RuntimeException("Only table output is supported. Use -t option.");
        switch (parameters.getType()) {
            case Alignments:
                processAlignments();
                break;
            case Cloneset:
                processClones();
                break;
        }
    }

    public void processAlignments() throws IOException {
        if (parameters.isTableView())
            printAlignmentsTableHeader();
        for (String inputFile : parameters.input) {
            if (parameters.isTableView())
                processAlignmentsFile(inputFile);
        }
    }

    public void processAlignmentsFile(String name) throws IOException {
        long size = Files.size(Paths.get(name));

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(name)) {
            long numberOfAlignedReads = 0;
            if (size > 30000000)
                SmartProgressReporter.startProgressReport("Processing " + name, reader, System.err);
            else
                System.err.println("Processing " + name + "...");
            for (VDJCAlignments alignments : CUtils.it(reader)) {
                numberOfAlignedReads++;
            }
            tableAdapter.row(name, reader.getNumberOfReads(), numberOfAlignedReads);
        }
    }

    public void printAlignmentsTableHeader() {
        tableAdapter.row("FileName", "NumberOfReads", "NumberOfAlignedReads");
    }

    public void processClones() {
        throw new RuntimeException("CLNS files not supported yet.");
    }

    @Override
    public String command() {
        return "info";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "Outputs information about mixcr binary file.")
    public static final class InfoParameters extends ActionParameters {
        @Parameter(description = "binary_file{.vdjca|.clns}[.gz]...")
        public List<String> input;

        @Parameter(description = "Output information as table.",
                names = {"-t", "--table"})
        public Boolean tableView = null;

        public boolean isTableView() {
            return tableView != null && tableView;
        }

        public FilesType getType() {
            if (input.isEmpty())
                throw new ParameterException("No files specified.");

            return FilesType.getType(input.get(0));
        }

        @Override
        public void validate() {
            FilesType type = getType();
            for (String fileName : input)
                if (FilesType.getType(fileName) != type)
                    throw new ParameterException("Mixed file types: " + fileName);
        }
    }

    public enum FilesType {
        Cloneset(".clns", ".clns.gz"),
        Alignments(".vdjca", ".vdjca.gz");
        final String[] extensions;

        FilesType(String... extensions) {
            this.extensions = extensions;
        }

        public boolean isOfType(String fileName) {
            for (String extension : extensions)
                if (fileName.endsWith(extension))
                    return true;
            return false;
        }

        public static FilesType getType(String fileName) {
            for (FilesType filesType : values())
                if (filesType.isOfType(fileName))
                    return filesType;
            throw new ParameterException("Unknown file type: " + fileName);
        }
    }

    public interface AlignmentInfoProvider {
        String header();

        String result();

        void onInit(VDJCAlignmentsReader reader);

        void onScan(VDJCAlignments alignment);
    }
}
