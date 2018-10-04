package com.milaboratory.mixcr.cli.newcli;

import cc.redberry.pipe.CUtils;
import com.beust.jcommander.ParameterException;
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

@Command(name = "info",
        sortOptions = true,
        hidden = true,
        separator = " ",
        description = "Outputs information about mixcr binary file.")
public class CommandInfo extends ACommand {
    @Parameters(description = "binary_file{.vdjca|.clns}[.gz]...", arity = "1..*")
    public List<String> input;

    @Option(description = "Output information as table.",
            names = {"-t", "--table"})
    public boolean tableView = false;

    public boolean isTableView() {
        return tableView;
    }

    public FilesType getType() {
        return FilesType.getType(input.get(0));
    }

    @Override
    public void validate() {
        super.validate();
        FilesType type = getType();
        for (String fileName : input)
            if (FilesType.getType(fileName) != type)
                throwValidationException("Mixed file types: " + fileName);
    }

    final PrintStream stream = System.out;
    final PrintStreamTableAdapter tableAdapter = new PrintStreamTableAdapter(stream);

    @Override
    public void run0() throws Exception {
        if (!tableView)
            throw new RuntimeException("Only table output is supported. Use -t option.");
        switch (getType()) {
            case Alignments:
                processAlignments();
                break;
            case Cloneset:
                processClones();
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
