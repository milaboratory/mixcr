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
import com.milaboratory.util.TempFileDest;
import com.milaboratory.util.TempFileManager;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class CommandFindAllelesTest {
    private final TempFileDest temp = TempFileManager.systemTempFolderDestination("test");
    private final Path file1 = temp.resolvePath("folder1/file1.clns");
    private final Path file2 = temp.resolvePath("folder2/file2.clns");
    private final Path file3 = temp.resolvePath("folder3/file1.clns");

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Before
    public void prepareFiles() throws IOException {
        file1.toFile().getParentFile().mkdirs();
        file1.toFile().createNewFile();
        file2.toFile().getParentFile().mkdirs();
        file2.toFile().createNewFile();
        file3.toFile().getParentFile().mkdirs();
        file3.toFile().createNewFile();
    }

    @Test
    public void outputsWrittenToCommonDirectory() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.COMMAND_NAME,
                "-o", "/output/folder/{file_name}_with_alleles.clns",
                file1.toString(),
                file2.toString()
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
                CommandFindAlleles.COMMAND_NAME,
                "-o", "{file_dir_path}/{file_name}_with_alleles.clns",
                file1.toString(),
                file2.toString()
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        assertTrue(command.getOutputFiles().get(0).toString(), command.getOutputFiles().get(0).toString().endsWith("/folder1/file1_with_alleles.clns"));
        assertTrue(command.getOutputFiles().get(1).toString(), command.getOutputFiles().get(1).toString().endsWith("/folder2/file2_with_alleles.clns"));
    }

    @Test
    public void includeLibraryIntoOutputs() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.COMMAND_NAME,
                "--export-library", "/output/folder/library.json",
                "-o", "/output/folder/{file_name}_with_alleles.clns",
                file1.toString(),
                file2.toString()
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
                CommandFindAlleles.COMMAND_NAME,
                "--export-library", "/output/folder/library.txt",
                "-o", "/output/folder/{file_name}_with_alleles.clns",
                file1.toString(),
                file2.toString()
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
                CommandFindAlleles.COMMAND_NAME,
                "-o", "/output/folder/{file_name}.clns",
                file1.toString(),
                file3.toString()
        ).getParseResult();
        CommandFindAlleles command = p.asCommandLineList().get(p.asCommandLineList().size() - 1).getCommand();
        try {
            command.getOutputFiles();
            fail();
        } catch (ValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Output clns files are not uniq:"));
        }
    }

    @Test
    public void templateMustBeClns() {
        CommandLine.ParseResult p = Main.parseArgs(
                CommandFindAlleles.COMMAND_NAME,
                "-o", "/output/folder/{file_name}.clna",
                file1.toString(),
                file2.toString()
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
