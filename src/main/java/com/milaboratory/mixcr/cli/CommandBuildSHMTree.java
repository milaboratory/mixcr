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
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.trees.*;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.math3.util.Pair;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@CommandLine.Command(name = CommandBuildSHMTree.BUILD_SHM_TREE_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Builds SHM trees.")
public class CommandBuildSHMTree extends ACommandWithSmartOverwriteMiXCR {
    static final String BUILD_SHM_TREE_COMMAND_NAME = "shm_tree";

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

    @CommandLine.Option(description = "SHM tree builder parameters preset.",
            names = {"-p", "--preset"})
    public String shmTreeBuilderParametersName = "default";

    private SHMTreeBuilderParameters shmTreeBuilderParameters = null;

    @Override
    public BuildSHMTreeConfiguration getConfiguration() {
        ensureParametersInitialized();
        return new BuildSHMTreeConfiguration(shmTreeBuilderParameters);
    }

    private void ensureParametersInitialized() {
        if (shmTreeBuilderParameters != null)
            return;

        shmTreeBuilderParameters = SHMTreeBuilderParametersPresets.getByName(shmTreeBuilderParametersName);
        if (shmTreeBuilderParameters == null)
            throwValidationException("Unknown parameters: " + shmTreeBuilderParametersName);
    }


    @Override
    public PipelineConfiguration getFullPipelineConfiguration() {
        return PipelineConfiguration.mkInitial(getInputFiles(), getConfiguration(),
                MiXCRVersionInfo.getAppVersionInfo());
    }

    @Override
    public void run1() throws Exception {
        BuildSHMTreeConfiguration configuration = getConfiguration();
        SHMTreeBuilder shmTreeBuilder = new SHMTreeBuilder(
                configuration.shmTreeBuilderParameters,
                new ClusteringCriteria.DefaultClusteringCriteria(),
                getInputFiles().stream()
                        .map(ExceptionUtil.wrap(path -> CloneSetIO.mkReader(Paths.get(path), VDJCLibraryRegistry.getDefault())))
                        .collect(Collectors.toList())
        );
        OutputPortCloseable<CloneWrapper> sortedClonotypes = shmTreeBuilder.sortClonotypes();
        OutputPortCloseable<Cluster> clusters = shmTreeBuilder.buildClusters(sortedClonotypes);

        NewickTreePrinter<CloneWrapper> mutationsPrinter = new NewickTreePrinter<>(c -> new StringBuilder()
                .append("V: ").append(mutations(c.clone, GeneType.Variable).collect(Collectors.toList()))
                .append(" J:").append(mutations(c.clone, GeneType.Joining).collect(Collectors.toList()))
                .toString(), false, false);

        NewickTreePrinter<CloneWrapper> idPrinter = new NewickTreePrinter<>(c -> String.valueOf(c.clone.getId()), false, false);

        Cluster cluster;
        while ((cluster = clusters.take()) != null) {
            List<Integer> VMutationPositions = allMutationPositions(cluster, GeneType.Variable);
            List<Integer> JMutationPositions = allMutationPositions(cluster, GeneType.Joining);

            List<String> mutations = cluster.cluster.stream()
                    .map(cw -> cw.clone)
                    .filter(clone -> ClusteringCriteria.numberOfMutations(clone, GeneType.Variable) +
                            ClusteringCriteria.numberOfMutations(clone, GeneType.Joining) > 0)
                    .map(clone -> new StringBuilder()
                            .append(clone.getBestHitGene(GeneType.Variable).getId().getName())
                            .append(clone.getBestHitGene(GeneType.Joining).getId().getName())
                            .append(" CDR3 length: ").append(clone.ntLengthOf(GeneFeature.CDR3))
                            .append(" ").append(clone.getFeature(GeneFeature.CDR3).getSequence())
                            .append(" ").append(String.format("%6d", clone.getId()))
                            .append(" V: ").append(mutationsRow(clone, GeneType.Variable, VMutationPositions))
                            .append(" J:").append(mutationsRow(clone, GeneType.Joining, JMutationPositions))
                            .toString()
                    )
                    .collect(Collectors.toList());
            if (mutations.size() > 30) {
//                if (true) {
                if (mutations.stream().anyMatch(it -> it.startsWith("IGHV3-30*00IGHJ4*00 CDR3 length: 42") && it.contains("7679"))) {
//                if (mutations.stream().anyMatch(it -> it.startsWith("IGHV3-48*00IGHJ4*00 CDR3 length: 57") && it.contains("2361") )
//                        || mutations.stream().anyMatch(it -> it.startsWith("IGHV3-48*00IGHJ6*00 CDR3 length: 66") && it.contains("18091"))) {
                    System.out.println("sequences:");
                    System.out.println(cluster.cluster.stream()
                            .map(it -> String.format("%6d", it.clone.getId()) + " " + it.clone.getTargets()[0].getSequence().toString() + " " + it.clone.getCount())
                            .collect(Collectors.joining("\n"))
                    );
                    System.out.println("\n");

                    System.out.println("without V/J mutations: " + cluster.cluster.stream().map(cw -> cw.clone)
                            .filter(clone -> ClusteringCriteria.numberOfMutations(clone, GeneType.Variable) +
                                    ClusteringCriteria.numberOfMutations(clone, GeneType.Joining) == 0).count() + "\n");

                    System.out.println("mutations:");
                    System.out.println(String.join("\n", mutations));
                    System.out.println("\n");

//            IGHV3-48*00IGHJ4*00 CDR3 length: 57
//            IGHV3-48*00IGHJ6*00 CDR3 length: 66
//
//            IGHV3-30*00IGHJ4*00 CDR3 length: 42
                    Collection<Tree<CloneWrapper>> trees = shmTreeBuilder.processCluster(cluster);
                    System.out.println(trees.stream().map(mutationsPrinter::print).collect(Collectors.joining("\n")));
                    System.out.println("\n");

                    System.out.println(trees.stream().map(idPrinter::print).collect(Collectors.joining("\n")));
                    System.out.println("\n");

                    System.out.println("ids:\n");
                    System.out.println(cluster.cluster.stream().map(it -> String.valueOf(it.clone.getId())).collect(Collectors.joining("|")));

                    System.out.println("\n\n");
                }
            }
        }
    }

    private List<Integer> allMutationPositions(Cluster cluster, GeneType geneType) {
        return cluster.cluster.stream()
                .map(cw -> cw.clone)
                .flatMap(clone -> mutations(clone, geneType))
                .flatMap(mutations -> IntStream.range(0, mutations.size())
                        .map(mutations::getMutation)
                        .mapToObj(Mutation::getPosition)
                )
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private String mutationsRow(Clone clone, GeneType variable, List<Integer> allMutationPositions) {
        Map<Object, List<Pair<Integer, String>>> allMutations = mutations(clone, variable)
                .flatMap(mutations -> IntStream.range(0, mutations.size())
                        .mapToObj(index -> Pair.create(
                                Mutation.getPosition(mutations.getMutation(index)),
                                Mutation.toString(mutations.getAlphabet(), mutations.getMutation(index))
                        )))
                .collect(Collectors.groupingBy(Pair::getKey, Collectors.toList()));
        return allMutationPositions.stream()
                .map(position -> String.format("%9s", allMutations.getOrDefault(position, Collections.emptyList()).stream().map(Pair::getValue).findFirst().orElse("")))
                .collect(Collectors.joining("|"));
    }

    private Stream<Mutations<NucleotideSequence>> mutations(Clone clone, GeneType geneType) {
        return Arrays.stream(clone.getBestHit(geneType).getAlignments())
                .filter(it -> it.getAbsoluteMutations().size() > 0)
                .map(Alignment::getAbsoluteMutations);
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    public static class BuildSHMTreeConfiguration implements ActionConfiguration<BuildSHMTreeConfiguration> {
        public final SHMTreeBuilderParameters shmTreeBuilderParameters;

        @JsonCreator
        public BuildSHMTreeConfiguration(
                @JsonProperty("shmTreeBuilderParameters") SHMTreeBuilderParameters shmTreeBuilderParameters
        ) {
            this.shmTreeBuilderParameters = shmTreeBuilderParameters;
        }

        @Override
        public String actionName() {
            return BUILD_SHM_TREE_COMMAND_NAME;
        }
    }
}
