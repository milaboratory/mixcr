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

import com.google.common.collect.Lists;
import com.milaboratory.cli.ValidationException;
import org.junit.Test;
import picocli.CommandLine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CommandFindAllelesTest {
    @Test
    public void outputsWrittenToCommonDirectory() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
                "/some/folder1/file1.clns", "/some/folder2/file2.clns",
                "/output/folder/{file_name}_with_alleles.clns"
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        assertEquals(
                Lists.newArrayList(
                        "/output/folder/file1_with_alleles.clns",
                        "/output/folder/file2_with_alleles.clns"
                ),
                command.getOutputFiles()
        );
    }

    @Test
    public void outputsWrittenToOriginalDirectory() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
                "/some/folder1/file1.clns", "/some/folder2/file2.clns",
                "{file_dir_path}/{file_name}_with_alleles.clns"
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        assertEquals(
                Lists.newArrayList(
                        "/some/folder1/file1_with_alleles.clns",
                        "/some/folder2/file2_with_alleles.clns"
                ),
                command.getOutputFiles()
        );
    }

    @Test
    public void includeLibraryIntoOutputs() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
                "--export-library", "/output/folder/library.json",
                "/some/folder1/file1.clns", "/some/folder2/file2.clns",
                "/output/folder/{file_name}_with_alleles.clns"
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        assertEquals(
                Lists.newArrayList(
                        "/output/folder/file1_with_alleles.clns",
                        "/output/folder/file2_with_alleles.clns",
                        "/output/folder/library.json"
                ),
                command.getOutputFiles()
        );
    }

    @Test
    public void libraryMustBeJson() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
                "--export-library", "/output/folder/library.txt",
                "/some/folder1/file1.clns", "/some/folder2/file2.clns",
                "/output/folder/{file_name}_with_alleles.clns"
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        try {
            command.validate();
            fail();
        } catch (ValidationException e) {
            assertEquals("--export-library must be json: /output/folder/library.txt", e.getMessage());
        }
    }

    @Test
    public void outputsMustBeUniq() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
                "/some/folder1/file.clns", "/some/folder2/file.clns",
                "/output/folder/{file_name}.clns"
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        try {
            command.getOutputFiles();
            fail();
        } catch (ValidationException e) {
            assertEquals("Output clns files are not uniq: [/output/folder/file.clns, /output/folder/file.clns]", e.getMessage());
        }
    }

    @Test
    public void templateMustBeClns() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
                "/some/folder1/file.clns", "/some/folder2/file.clns",
                "/output/folder/{file_name}.clna"
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        try {
            command.getOutputFiles();
            fail();
        } catch (ValidationException e) {
            assertEquals("Wrong template: command produces only clns /output/folder/{file_name}.clna", e.getMessage());
        }
    }
}
