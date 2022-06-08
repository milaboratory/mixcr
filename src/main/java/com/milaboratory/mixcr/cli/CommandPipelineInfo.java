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
        System.out.println(GlobalObjectMappers.getPretty().writeValueAsString(PipelineConfigurationReaderMiXCR
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
