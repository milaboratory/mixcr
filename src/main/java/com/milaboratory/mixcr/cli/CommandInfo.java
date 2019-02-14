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
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.util.PrintStreamTableAdapter;
import com.milaboratory.util.SmartProgressReporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.IOUtil.*;

@Command(name = "info",
        sortOptions = true,
        hidden = true,
        separator = " ",
        description = "Outputs information about mixcr binary file.")
public class CommandInfo extends ACommandMiXCR {
    @Parameters(description = "binary_file{.vdjca|.clns}...", arity = "1..*")
    public List<String> input;

    @Option(description = "Output information as table.",
            names = {"-t", "--table"})
    public boolean tableView = false;

    public boolean isTableView() {
        return tableView;
    }

    private IOUtil.MiXCRFileInfo info0 = null;

    public String getType() {
        if (info0 == null)
            info0 = (IOUtil.MiXCRFileInfo) fileInfoExtractorInstance.getFileInfo(input.get(0));
        return info0.fileType;
    }

    @Override
    public void validate() {
        super.validate();
        String type = getType();
        for (String fileName : input)
            if (!fileInfoExtractorInstance.getFileInfo(fileName).fileType.equals(type))
                throwValidationException("Mixed file types: " + fileName);
    }

    final PrintStream stream = System.out;
    final PrintStreamTableAdapter tableAdapter = new PrintStreamTableAdapter(stream);

    @Override
    public void run0() throws Exception {
        if (!tableView)
            throw new RuntimeException("Only table output is supported. Use -t option.");

        switch (getType()) {
            case MAGIC_CLNA:
            case MAGIC_CLNS:
                processClones();
                break;
            case MAGIC_VDJC:
                processAlignments();
                break;
        }
    }

    public void processAlignments() throws IOException {
        if (tableView)
            printAlignmentsTableHeader();
        for (String inputFile : input) {
            if (tableView)
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
            for (VDJCAlignments __ : CUtils.it(reader))
                numberOfAlignedReads++;
            tableAdapter.row(name, reader.getNumberOfReads(), numberOfAlignedReads);
        }
    }

    public void printAlignmentsTableHeader() {
        tableAdapter.row("FileName", "NumberOfReads", "NumberOfAlignedReads");
    }

    public void processClones() {
        throw new RuntimeException("CLNS files not supported yet.");
    }

    public interface AlignmentInfoProvider {
        String header();

        String result();

        void onInit(VDJCAlignmentsReader reader);

        void onScan(VDJCAlignments alignment);
    }
}
