package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.mixcr.basictypes.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReader;
import com.milaboratory.mixcr.util.PrintStreamTableAdapter;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.LightFileDescriptor;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
public class ActionPipelineInfo implements Action {
    public PipelineInfoParameters parameters = new PipelineInfoParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        if (parameters.json())
            analysisPipelineInfoJson(parameters.input());
        else
            analysisPipelineInfo(parameters.input());
    }

    @Override
    public String command() {
        return "pipelineInfo";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    public static void analysisPipelineInfoJson(String file) throws JsonProcessingException {
        System.out.println(GlobalObjectMappers.PRETTY.writeValueAsString(PipelineConfigurationReader.fromFile(file)));
    }

    public static void analysisPipelineInfo(String file) {
        PipelineConfiguration pipeline = PipelineConfigurationReader.fromFile(file);
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

    //mixcr pipelineInfo example.clns | tail -n +2 | column -t -s$'\t'
    @Parameters(commandDescription = "Outputs information about analysis pipeline of MiXCR binary file.")
    public static final class PipelineInfoParameters extends ActionParameters {
        @Parameter(description = "binary_file{.vdjca|.clns|.clna}[.gz]...")
        public List<String> input;

        public String input() {
            return input.get(0);
        }

        @Parameter(description = "Print pipeline info in JSON format.",
                names = {"--json"})
        public boolean json = false;

        public boolean json() {
            return json;
        }

        @Override
        public void validate() {
            if (input.size() != 1)
                throw new ParameterException("should pass exactly one input file");
            super.validate();
        }
    }
}
