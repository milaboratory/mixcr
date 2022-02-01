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

import cc.redberry.pipe.OutputPortCloseable;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.alleles.Allele;
import com.milaboratory.mixcr.alleles.AllelesSearcher;
import com.milaboratory.mixcr.alleles.FindAllelesParameters;
import com.milaboratory.mixcr.alleles.FindAllelesParametersPresets;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(name = CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Find allele variants in clns.")
public class CommandFindAlleles extends ACommandWithSmartOverwriteMiXCR {
    static final String FIND_ALLELES_COMMAND_NAME = "find_alleles";

    @CommandLine.Parameters(
            arity = "2..*",
            description = "input_file.clns [input_file2.clns ....] output_file.tree"
    )
    private List<String> inOut = new ArrayList<>();

    @Override
    public List<String> getInputFiles() {
        return inOut.subList(0, inOut.size() - 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        return inOut.subList(inOut.size() - 1, inOut.size());
    }

    @CommandLine.Option(description = "Find alleles parameters preset.",
            names = {"-p", "--preset"})
    public String findAllelesParametersName = "default";

    private FindAllelesParameters findAllelesParameters = null;

    @Override
    public FindAllelesConfiguration getConfiguration() {
        ensureParametersInitialized();
        return new FindAllelesConfiguration(findAllelesParameters);
    }

    private void ensureParametersInitialized() {
        if (findAllelesParameters != null)
            return;

        findAllelesParameters = FindAllelesParametersPresets.getByName(findAllelesParametersName);
        if (findAllelesParameters == null)
            throwValidationException("Unknown parameters: " + findAllelesParametersName);
    }


    @Override
    public PipelineConfiguration getFullPipelineConfiguration() {
        return PipelineConfiguration.mkInitial(getInputFiles(), getConfiguration(),
                MiXCRVersionInfo.getAppVersionInfo());
    }

    @Override
    public void run1() throws Exception {
        FindAllelesConfiguration configuration = getConfiguration();
        List<CloneReader> cloneReaders = getInputFiles().stream()
                .map(ExceptionUtil.wrap(path -> CloneSetIO.mkReader(Paths.get(path), VDJCLibraryRegistry.getDefault())))
                .collect(Collectors.toList());
        AllelesSearcher allelesSearcher = new AllelesSearcher(configuration.findAllelesParameters, cloneReaders);
        AllelesSearcher.SortedClonotypes sortedClonotypes = allelesSearcher.sortClonotypes();

        OutputPortCloseable<Cluster<Clone>> clustersByV = allelesSearcher.buildClusters(sortedClonotypes.getSortedByV(), GeneType.Variable);

        List<Allele> result = new ArrayList<>();
        Cluster<Clone> cluster;
        while ((cluster = clustersByV.take()) != null) {
            result.addAll(allelesSearcher.findAlleles(cluster, GeneType.Variable));
        }

        OutputPortCloseable<Cluster<Clone>> clustersByJ = allelesSearcher.buildClusters(sortedClonotypes.getSortedByJ(), GeneType.Joining);
        while ((cluster = clustersByJ.take()) != null) {
            result.addAll(allelesSearcher.findAlleles(cluster, GeneType.Joining));
        }
        System.out.println(result.size());
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    public static class FindAllelesConfiguration implements ActionConfiguration<FindAllelesConfiguration> {
        public final FindAllelesParameters findAllelesParameters;

        @JsonCreator
        public FindAllelesConfiguration(
                @JsonProperty("findAllelesParameters") FindAllelesParameters findAllelesParameters
        ) {
            this.findAllelesParameters = findAllelesParameters;
        }

        @Override
        public String actionName() {
            return FIND_ALLELES_COMMAND_NAME;
        }
    }
}
