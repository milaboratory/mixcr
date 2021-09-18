/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReaderMiXCR;
import com.milaboratory.mixcr.util.PrintStreamTableAdapter;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.LightFileDescriptor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

@Command(name = "pipelineInfo",
        hidden = true,
        separator = " ",
        description = "List all available library by scanning all library search paths.")
public class CommandPipelineInfo extends ACommandMiXCR {
    @Parameters(description = "binary_file{.vdjca|.clns|.clna}...")
    public String input;

    @Option(description = "Print pipeline info in JSON format.",
            names = {"--json"})
    public boolean json = false;

    @Override
    public void run0() throws Exception {
        if (json)
            analysisPipelineInfoJson(input);
        else
            analysisPipelineInfo(input);
    }

    public static void analysisPipelineInfoJson(String file) throws JsonProcessingException {
        System.out.println(GlobalObjectMappers.PRETTY.writeValueAsString(PipelineConfigurationReaderMiXCR
                .sFromFile(file)));
    }

    public static void analysisPipelineInfo(String file) {
        PipelineConfiguration pipeline = PipelineConfigurationReaderMiXCR.sFromFile(file);
        System.out.println("Pipeline:");
        final PrintStreamTableAdapter tableAdapter = new PrintStreamTableAdapter(System.out);
        for (int i = 0; i < pipeline.pipelineSteps.length; ++i) {
            LightFileDescriptor[] in = pipeline.pipelineSteps[i].inputFilesDescriptors;
            LightFileDescriptor[] out = i == pipeline.pipelineSteps.length - 1
                    ? new LightFileDescriptor[]{LightFileDescriptor.calculate(new File(file).toPath())}
                    : pipeline.pipelineSteps[i + 1].inputFilesDescriptors;

            tableAdapter.row(pipeline.pipelineSteps[i].configuration.actionName(),
                    Arrays.stream(in).map(f -> f.name).collect(Collectors.joining(" ")),
                    "->",
                    Arrays.stream(out).map(f -> f.name).collect(Collectors.joining(" ")));
        }
    }
}
