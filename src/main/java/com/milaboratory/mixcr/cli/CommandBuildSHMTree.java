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
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceWithQuality;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.trees.*;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import io.repseq.core.*;
import org.apache.commons.math3.util.Pair;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.milaboratory.mixcr.trees.ClusteringCriteria.getAbsoluteMutationsWithoutCDR3;
import static com.milaboratory.mixcr.trees.ClusteringCriteria.numberOfMutations;
import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.*;

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
        OutputPortCloseable<Cluster<CloneWrapper>> clusters = shmTreeBuilder.buildClusters(sortedClonotypes);

        NewickTreePrinter<ObservedOrReconstructed<CloneWrapper, NucleotideSequence>> mutationsPrinter = new NewickTreePrinter<>(
                node -> node.convert(c -> new StringBuilder()
                                .append("V: ").append(mutations(c.clone, Variable).collect(Collectors.toList()))
                                .append(" J:").append(mutations(c.clone, Joining).collect(Collectors.toList()))
                                .toString(),
                        it -> ""
                ),
                false, false
        );

        NewickTreePrinter<ObservedOrReconstructed<CloneWrapper, NucleotideSequence>> idPrinter = new NewickTreePrinter<>(
                node -> node.convert(c -> String.valueOf(c.clone.getId()), it -> ""),
                false, false
        );

        Cluster<CloneWrapper> clusterTemp;
        while ((clusterTemp = clusters.take()) != null) {
            Cluster<CloneWrapper> cluster = clusterTemp;
            long mutationsCount = cluster.cluster.stream()
                    .map(cw -> cw.clone)
                    .filter(clone -> numberOfMutations(clone, Variable) + numberOfMutations(clone, Joining) > 0)
                    .count();
            if (true) {
//            if (mutationsCount > 30) {
                //                if (true) {
                if (cluster.cluster.stream().map(it -> it.clone.getId()).anyMatch(it -> it == 7679)) {
//                if (mutations.stream().anyMatch(it -> it.startsWith("IGHV3-48*00IGHJ4*00 CDR3 length: 57") && it.contains("2361") )
//                        || mutations.stream().anyMatch(it -> it.startsWith("IGHV3-48*00IGHJ6*00 CDR3 length: 66") && it.contains("18091"))) {

                    System.out.println("V gene:");
                    System.out.println(cluster.cluster.get(0).clone.getBestHit(Variable).getAlignment(0).getSequence1());
                    System.out.println("J gene:");
                    System.out.println(cluster.cluster.get(0).clone.getBestHit(Joining).getAlignment(0).getSequence1());
                    System.out.println("\n");

                    System.out.println("sequences:");
                    System.out.println(cluster.cluster.stream()
                            .map(it -> String.format("%6d %s - %s %s %s %f",
                                    it.clone.getId(),
                                    it.clone.getFeature(CDR3).getSequence(),
                                    it.clone.getFeature(new GeneFeature(CDR3Begin, VEndTrimmed)).getSequence(),
                                    it.clone.getFeature(new GeneFeature(VEndTrimmed, JBeginTrimmed)).getSequence(),
                                    it.clone.getFeature(new GeneFeature(JBeginTrimmed, CDR3End)).getSequence(),
                                    it.clone.getCount()
                            ))
                            .collect(Collectors.joining("\n"))
                    );
                    System.out.println("\n");
                    System.out.println(cluster.cluster.stream()
                            .map(it -> String.format("%6d", it.clone.getId()) + " " + it.clone.getTargets()[0].getSequence().toString() + " " + it.clone.getCount())
                            .collect(Collectors.joining("\n"))
                    );
                    System.out.println("\n");

                    System.out.println("without V/J mutations: " + cluster.cluster.stream().map(cw -> cw.clone)
                            .filter(clone -> numberOfMutationsWithoutCDR3(clone, Variable) +
                                    numberOfMutationsWithoutCDR3(clone, Joining) == 0).count() + "\n");


                    Optional<Clone> rootClone = cluster.cluster.stream().map(cw -> cw.clone).filter(it -> it.getId() == 24722).findFirst();

                    System.out.println("D genes sum score:");
                    System.out.println(cluster.cluster.subList(13, cluster.cluster.size())
                            .stream()
                            .flatMap(it -> Arrays.stream(it.clone.getHits(GeneType.Diversity)))
                            .collect(Collectors.groupingBy(VDJCHit::getGene, Collectors.summingDouble(VDJCHit::getScore)))
                            .entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByValue())
                            .map(e -> {
                                VDJCGene dGene = e.getKey();
                                Alignment<NucleotideSequence> bestDAlignment = cluster.cluster.subList(13, cluster.cluster.size())
                                        .stream()
                                        .flatMap(it -> Arrays.stream(it.clone.getHits(GeneType.Diversity)))
                                        .filter(it -> it.getGene().equals(dGene))
                                        .map(it -> it.getAlignment(0))
                                        .max(Comparator.comparing(Alignment::getScore))
                                        .get();

                                NucleotideSequence dSequence = bestDAlignment.getSequence1().getRange(bestDAlignment.getSequence1Range());
                                return dSequence.toString() + " - " + e.getValue();
                            })
                            .collect(Collectors.joining("\n")));
                    System.out.println("\n");


                    List<Integer> VMutationPositions = allMutationPositions(cluster, Variable);
                    List<Integer> CDR3MutationPositions;
                    if (rootClone.isPresent()) {
                        CDR3MutationPositions = allMutationPositionsInCDR3(cluster, rootClone.get());
                    } else {
                        CDR3MutationPositions = Collections.emptyList();
                    }
                    List<Integer> JMutationPositions = allMutationPositions(cluster, Joining);

                    List<String> mutations = cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .filter(clone -> numberOfMutations(clone, Variable) + numberOfMutations(clone, Joining) > 0)
                            .map(clone -> {
                                        StringBuilder stringBuilder = new StringBuilder();
                                        stringBuilder
                                                .append(clone.getBestHitGene(Variable).getId().getName())
                                                .append(clone.getBestHitGene(Joining).getId().getName())
                                                .append(" CDR3 length: ")
                                                .append(clone.ntLengthOf(CDR3))
                                                .append(" ").append(Optional.ofNullable(clone.getFeature(new GeneFeature(CDR3Begin, VEndTrimmed))).map(SequenceWithQuality::getSequence).orElse(null))
                                                .append(" ").append(Optional.ofNullable(clone.getFeature(new GeneFeature(VEndTrimmed, JBeginTrimmed))).map(SequenceWithQuality::getSequence).orElse(null))
                                                .append(" ").append(Optional.ofNullable(clone.getFeature(new GeneFeature(JBeginTrimmed, CDR3End))).map(SequenceWithQuality::getSequence).orElse(null))
                                                .append(" ").append(String.format("%6d", clone.getId()))
                                                .append(" V: ").append(mutationsRow(clone, Variable, VMutationPositions));
                                        rootClone.ifPresent(root -> stringBuilder.append(" CDR3:").append(CDR3mutationsRow(clone, root, CDR3MutationPositions)));
                                        stringBuilder.append(" J:").append(mutationsRow(clone, Joining, JMutationPositions));
                                        return stringBuilder.toString();
                                    }
                            )
                            .collect(Collectors.toList());

                    System.out.println("mutations:");
                    System.out.println(String.join("\n", mutations));
                    System.out.println("\n");

                    System.out.println("mutation rate:");
                    System.out.println(cluster.cluster.stream()
                            .map(cloneWrapper -> cloneWrapper.clone)
                            .map(clone -> String.format(
                                    "%6d %.4f (%d) %.4f (%d)",
                                    clone.getId(),
                                    numberOfMutationsWithoutCDR3(clone, Variable) / (double) ntLengthOfWithoutCDR3(clone, Variable),
                                    numberOfMutationsWithoutCDR3(clone, Variable),
                                    numberOfMutationsWithoutCDR3(clone, Joining) / (double) ntLengthOfWithoutCDR3(clone, Joining),
                                    numberOfMutationsWithoutCDR3(clone, Joining)
                            )).collect(Collectors.joining("\n")));
                    System.out.println("\n");

                    System.out.println("CDR3 comparison:\n");
                    System.out.println("      |" + cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%6d", clone.getId()))
                            .collect(Collectors.joining("|"))
                    );

                    System.out.println(cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%6d|", clone.getId()) + cluster.cluster.stream()
                                    .map(cw -> cw.clone)
                                    .map(compareWith -> String.format("%6d", mutationsBetween(clone, compareWith, CDR3).size()))
                                    .collect(Collectors.joining("|"))
                            )
                            .collect(Collectors.joining("\n"))
                    );
                    System.out.println("\n");

                    System.out.println("V|CDR3|J comparison:\n");
                    System.out.println("      |" + cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%10d", clone.getId()))
                            .collect(Collectors.joining("|"))
                    );

                    System.out.println(cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%6d|", clone.getId()) + cluster.cluster.stream()
                                    .map(cw -> cw.clone)
                                    .map(compareWith -> String.format(" %2d|%2d|%2d ",
                                            mutationsBetweenWithoutCDR3(clone, compareWith, Variable).size(),
                                            mutationsBetween(clone, compareWith, CDR3).size(),
                                            mutationsBetweenWithoutCDR3(clone, compareWith, Joining).size()
                                    ))
                                    .collect(Collectors.joining("|"))
                            )
                            .collect(Collectors.joining("\n"))
                    );
                    System.out.println("\n");

                    System.out.println("mutations rate diff:\n");
                    System.out.println("       |" + cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%12d    ", clone.getId()))
                            .collect(Collectors.joining("|"))
                    );

                    System.out.println(cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%6d |", clone.getId()) + cluster.cluster.stream()
                                    .map(cw -> cw.clone)
                                    .map(compareWith -> {
                                        int vMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Variable).size();
                                        int jMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Joining).size();
                                        int cdr3MutationsBetween = mutationsBetween(clone, compareWith, CDR3).size();
                                        int VPlusJLength = ntLengthOfWithoutCDR3(clone, Variable) + ntLengthOfWithoutCDR3(clone, Joining);
                                        int CDR3Length = clone.ntLengthOf(CDR3);
                                        double normalizedDistanceFromCloneToGermline = (numberOfMutationsWithoutCDR3(clone, Variable) + numberOfMutationsWithoutCDR3(clone, Joining)) / (double) VPlusJLength;
                                        double normalizedDistanceFromCompareToGermline = (numberOfMutationsWithoutCDR3(compareWith, Variable) + numberOfMutationsWithoutCDR3(compareWith, Joining)) / (double) VPlusJLength;
                                        double normalizedAverageDistanceToGermline = (normalizedDistanceFromCloneToGermline + normalizedDistanceFromCompareToGermline) / 2.0;
                                        double normalizedDistanceBetweenClones = (vMutationsBetween + jMutationsBetween + cdr3MutationsBetween) / (double) (VPlusJLength + CDR3Length);
                                        double normalizedDistanceBetweenClonesInCDR3 = (cdr3MutationsBetween) / (double) CDR3Length;
                                        return String.format(" %.2f|%.2f|%.2f ",
                                                normalizedAverageDistanceToGermline * 20,
                                                normalizedDistanceBetweenClones * 10,
                                                normalizedDistanceBetweenClonesInCDR3 * 10
                                        );
                                    })
                                    .collect(Collectors.joining("|"))
                            )
                            .collect(Collectors.joining("\n"))
                    );
                    System.out.println("\n");

                    System.out.println("clustering fomula:\n");
                    System.out.println("      |" + cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%6d ", clone.getId()))
                            .collect(Collectors.joining("|"))
                    );

                    System.out.println(cluster.cluster.stream()
                            .map(cw -> cw.clone)
                            .map(clone -> String.format("%6d|", clone.getId()) + cluster.cluster.stream()
                                    .map(cw -> cw.clone)
                                    .map(compareWith -> {
                                        int vMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Variable).size();
                                        int jMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Joining).size();
                                        int cdr3MutationsBetween = mutationsBetween(clone, compareWith, CDR3).size();
                                        int VPlusJLength = ntLengthOfWithoutCDR3(clone, Variable) + ntLengthOfWithoutCDR3(clone, Joining);
                                        int CDR3Length = clone.ntLengthOf(CDR3);
                                        double normalizedDistanceFromCloneToGermline = (numberOfMutationsWithoutCDR3(clone, Variable) + numberOfMutationsWithoutCDR3(clone, Joining)) / (double) VPlusJLength;
                                        double normalizedDistanceFromCompareToGermline = (numberOfMutationsWithoutCDR3(compareWith, Variable) + numberOfMutationsWithoutCDR3(compareWith, Joining)) / (double) VPlusJLength;
                                        double normalizedAverageDistanceToGermline = (normalizedDistanceFromCloneToGermline + normalizedDistanceFromCompareToGermline) / 2.0;
                                        double normalizedDistanceBetweenClones = (vMutationsBetween + jMutationsBetween + cdr3MutationsBetween) / (double) (VPlusJLength + CDR3Length);
                                        double normalizedDistanceBetweenClonesInCDR3 = (cdr3MutationsBetween) / (double) CDR3Length;
                                        return String.format("%.5f",
                                                normalizedDistanceBetweenClonesInCDR3 + (normalizedDistanceBetweenClones - normalizedAverageDistanceToGermline)
                                        );
                                    })
                                    .collect(Collectors.joining("|"))
                            )
                            .collect(Collectors.joining("\n"))
                    );
                    System.out.println("\n");

//            IGHV3-48*00IGHJ4*00 CDR3 length: 57
//            IGHV3-48*00IGHJ6*00 CDR3 length: 66
//
//            IGHV3-30*00IGHJ4*00 CDR3 length: 42
                    Collection<Tree<ObservedOrReconstructed<CloneWrapper, NucleotideSequence>>> trees = shmTreeBuilder.processCluster(cluster);
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

    private int ntLengthOfWithoutCDR3(Clone clone, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        Alignment<NucleotideSequence> alignment = bestHit.getAlignment(0);
        if (geneType == GeneType.Variable) {
            return alignment.convertToSeq1Position(bestHit.getPosition(0, CDR3Begin)) - alignment.getSequence1Range().getLower();
        } else if (geneType == GeneType.Joining) {
            return alignment.getSequence1Range().getUpper() - alignment.convertToSeq1Position(bestHit.getPosition(0, ReferencePoint.CDR3End));
        } else {
            throw new IllegalArgumentException();
        }
    }

    private Mutations<NucleotideSequence> mutationsBetween(Clone clone, Clone compareWith, GeneFeature geneFeature) {
        return Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                clone.getNFeature(geneFeature),
                compareWith.getNFeature(geneFeature)
        ).getAbsoluteMutations();
    }

    private Mutations<NucleotideSequence> mutationsBetweenWithoutCDR3(Clone clone, Clone compareWith, GeneType geneType) {
        Mutations<NucleotideSequence> firstMutations = getAbsoluteMutationsWithoutCDR3(clone, geneType);
        Mutations<NucleotideSequence> lastMutations = getAbsoluteMutationsWithoutCDR3(compareWith, geneType);

        return firstMutations.invert().combineWith(lastMutations);
    }

    private List<Integer> allMutationPositions(Cluster<CloneWrapper> cluster, GeneType geneType) {
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

    private List<Integer> allMutationPositionsInCDR3(Cluster<CloneWrapper> cluster, Clone root) {
        return cluster.cluster.stream()
                .map(cw -> cw.clone)
                .map(clone -> mutationsBetween(root, clone, CDR3))
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

    private String CDR3mutationsRow(Clone clone, Clone root, List<Integer> allMutationPositions) {
        Mutations<NucleotideSequence> mutations = mutationsBetween(root, clone, CDR3);
        Map<Object, List<Pair<Integer, String>>> allMutations = IntStream.range(0, mutations.size())
                .mapToObj(index -> Pair.create(
                        Mutation.getPosition(mutations.getMutation(index)),
                        Mutation.toString(mutations.getAlphabet(), mutations.getMutation(index))
                ))
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

    static int numberOfMutationsWithoutCDR3(Clone clone, GeneType geneType) {
        return getAbsoluteMutationsWithoutCDR3(clone, geneType).size();
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
