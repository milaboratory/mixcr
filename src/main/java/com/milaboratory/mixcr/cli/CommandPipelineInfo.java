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
