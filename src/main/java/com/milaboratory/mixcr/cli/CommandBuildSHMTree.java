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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceWithQuality;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.trees.*;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.mixcr.util.Java9Util;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.math3.util.Pair;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.milaboratory.mixcr.trees.ClusteringCriteria.CDR3Sequence1Range;
import static com.milaboratory.mixcr.trees.ClusteringCriteria.getMutationsWithoutCDR3;
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

        NewickTreePrinter<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> idPrinter = new NewickTreePrinter<>(
                node -> node.getContent().convert(c -> String.valueOf(c.clone.getId()), it -> ""),
                false, false
        );

        Cluster<CloneWrapper> clusterTemp;
        while ((clusterTemp = clusters.take()) != null) {
            Cluster<CloneWrapper> cluster = clusterTemp;
            boolean print = false;

            if (true) {
//            if (mutationsCount > 30) {
                //                if (true) {
                if (cluster.cluster.stream().map(it -> it.clone.getId()).anyMatch(it -> it == 7679)) {
//                if (mutations.stream().anyMatch(it -> it.startsWith("IGHV3-48*00IGHJ4*00 CDR3 length: 57") && it.contains("2361") )
//                        || mutations.stream().anyMatch(it -> it.startsWith("IGHV3-48*00IGHJ6*00 CDR3 length: 66") && it.contains("18091"))) {

                    Set<Integer> clonesDefinitelyInTree = Sets.newHashSet(
                            //TODO consult
                            29944,
                            15729,
                            20946,
                            25712,
                            21841,


                            //TODO consult
                            4689,
                            5113,
                            36487,
                            48367,


                            24722,
                            13552,
                            39832,
                            22831,
                            29919,
                            49407,
                            20908,
                            11055,
                            44141,
                            47669,
                            17342,
                            24733,
                            26817,
                            33091,
                            37617,
                            11054,
                            35381,
                            50034,
                            34317,
                            32066,
                            17340,
                            38713,
                            29946,
                            6936,
                            40966,
                            1114,
                            32092,
                            27827,
                            44143,
                            36514,
                            561,
                            17363,
                            9919,
                            24732,
                            33139,
                            43129,
                            16459,
                            8481,
                            28875,
                            12221,
                            16514,
                            11015,
                            37588,
                            32105,
                            22866,
                            18210,
                            22839,
                            38699,
                            32080,
                            46060,
                            32093,
                            12280
                    );

                    Set<Integer> clonesMaybeInTree = Sets.newHashSet(
                            27, //TODO consult
                            17365, //TODO consult


                            19129,
                            41109,
                            37766,
                            15694,
                            18208
                    );

                    List<Set<Integer>> anotherTrees = Lists.newArrayList(
                            Sets.newHashSet(
                                    46050,
                                    444,
                                    6935
                            ),
                            Sets.newHashSet(
                                    21022,
                                    9930
                            )
                    );

                    Set<Integer> clonesWithMutationsButNotInMainTree = Sets.newHashSet(
                            19143,
                            9920,
                            1194,
                            2889,
                            44140,
                            20062,
                            39858
                    );


                    if (print) {
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


                        int rootCloneId = 19129;
//                        int rootCloneId = 24722;
                        Optional<Clone> rootClone = cluster.cluster.stream().map(cw -> cw.clone).filter(it -> it.getId() == rootCloneId).findFirst();

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

                        Predicate<Clone> clonesForComparisonFilter = it -> clonesDefinitelyInTree.contains(it.getId());
                        boolean skipMismatch = false;

                        List<Pair<Clone, Pair<Pair<FitnessFunctionParams, Clone>, List<Integer>>>> cloneComparisonParams = cluster.cluster.stream()
                                .map(cw -> cw.clone)
                                .map(clone -> {
                                            Pair<FitnessFunctionParams, Clone> minFitnessFunctionParams = cluster.cluster.stream()
                                                    .map(cw -> cw.clone)
                                                    .filter(clonesForComparisonFilter)
                                                    .filter(compareWith -> compareWith.getId() != clone.getId())
                                                    .map(compareWith -> Pair.create(fitnessFunctionParams(clone, compareWith), compareWith))
//                                                    .filter(it -> fitnessFunction(it.getFirst()) > 0.0)
                                                    .min(Comparator.comparing(it -> fitnessFunction(it.getFirst()))).orElseThrow(IllegalArgumentException::new);

                                            List<Integer> minMutations = cluster.cluster.stream()
                                                    .map(cw -> cw.clone)
                                                    .filter(clonesForComparisonFilter)
                                                    .filter(compareWith -> compareWith.getId() != clone.getId())
                                                    .map(compareWith -> {
                                                        int vMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Variable).size();
                                                        int jMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Joining).size();
                                                        int cdr3MutationsBetween = alignmentOfCDR3(clone, compareWith).getAbsoluteMutations().size();
                                                        return Lists.newArrayList(vMutationsBetween, cdr3MutationsBetween, jMutationsBetween);
                                                    })
                                                    .min(Comparator.comparing(it -> it.get(0) + it.get(1))).orElseThrow(IllegalArgumentException::new);
                                            return Pair.create(clone, Pair.create(minFitnessFunctionParams, minMutations));
                                        }
                                )
                                .sorted(Comparator.comparing(it -> fitnessFunction(it.getSecond().getFirst().getFirst())))
                                .collect(Collectors.toList());
                        double lastFitnessFunction = 0.0;

                        for (int i = 0; i < cloneComparisonParams.size(); i++) {
                            Pair<Clone, Pair<Pair<FitnessFunctionParams, Clone>, List<Integer>>> cloneComparisonParam = cloneComparisonParams.get(i);
                            FitnessFunctionParams fitnessFunctionParams = cloneComparisonParam.getSecond().getFirst().getFirst();
                            double fitnessFunctionResult = fitnessFunction(fitnessFunctionParams);
                            Clone clone = cloneComparisonParam.getFirst();
                            Clone compareWith = cloneComparisonParam.getSecond().getFirst().getSecond();

                            int vMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Variable).size();
                            int jMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Joining).size();
                            int cdr3MutationsBetween = alignmentOfCDR3(clone, compareWith).getAbsoluteMutations().size();

                            String match;
                            if (clonesMaybeInTree.contains(clone.getId()) || clonesMaybeInTree.contains(compareWith.getId())) {
                                match = "?";
                            } else if (clonesDefinitelyInTree.contains(clone.getId()) && clonesDefinitelyInTree.contains(compareWith.getId())) {
                                match = "true";
                            } else if (!clonesDefinitelyInTree.contains(clone.getId()) && !clonesDefinitelyInTree.contains(compareWith.getId())) {
                                match = "true";
                            } else {
                                match = "false";
                            }

                            if (!match.equals("false") && skipMismatch) {
                                continue;
                            }

                            String format = String.format("%3d|%6d|%6d|%5s|%6d%s(%.5f)|  %2d|%2d|%2d|  %2d|%2d|%2d|  %2d|  %.5f|%.5f|%.5f|%.5f|  %.5f|%.5f|%.5f",
                                    i,
                                    clone.getId(),
                                    compareWith.getId(),
                                    match,
                                    (int) Math.floor(fitnessFunctionResult),
                                    String.format("%.5f", fitnessFunctionResult - Math.floor(fitnessFunctionResult)).substring(1),
                                    fitnessFunctionResult - lastFitnessFunction,
                                    vMutationsBetween,
                                    cdr3MutationsBetween,
                                    jMutationsBetween,
                                    cloneComparisonParam.getSecond().getSecond().get(0),
                                    cloneComparisonParam.getSecond().getSecond().get(1),
                                    cloneComparisonParam.getSecond().getSecond().get(2),
                                    numberOfMutationsWithoutCDR3(clone, Variable) + numberOfMutationsWithoutCDR3(clone, Joining),
                                    fitnessFunctionParams.distanceBetweenClonesInCDR3,
                                    fitnessFunctionParams.distanceBetweenClonesWithoutCDR3,
                                    fitnessFunctionParams.distanceBetweenClones,
                                    fitnessFunctionParams.minDistanceToGermline,
                                    fitnessFunctionFirstPart(fitnessFunctionParams),
                                    fitnessFunctionSecondPart(fitnessFunctionParams),
                                    fitnessFunctionThirdPart(fitnessFunctionParams)
                            );
                            lastFitnessFunction = fitnessFunctionResult;
                            System.out.println(format);
                        }
                        System.out.println("\n");

                        if (true) {
                            break;
                        }

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
                                        .map(compareWith -> String.format("%6d", alignmentOfCDR3(clone, compareWith).getAbsoluteMutations().size()))
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
                                                alignmentOfCDR3(clone, compareWith).getAbsoluteMutations().size(),
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
                                            FitnessFunctionParams fitnessFunctionParams = fitnessFunctionParams(clone, compareWith);
                                            return String.format(" %.2f|%.2f|%.2f ",
                                                    fitnessFunctionParams.minDistanceToGermline * 20,
                                                    fitnessFunctionParams.distanceBetweenClones * 10,
                                                    fitnessFunctionParams.distanceBetweenClonesInCDR3 * 10
                                            );
                                        })
                                        .collect(Collectors.joining("|"))
                                )
                                .collect(Collectors.joining("\n"))
                        );
                        System.out.println("\n");

                        System.out.println("clustering formula:\n");
                        System.out.println("      |  min  |" + cluster.cluster.stream()
                                .map(cw -> cw.clone)
                                .map(clone -> String.format("%6d ", clone.getId()))
                                .collect(Collectors.joining("|"))
                        );

                        System.out.println(cluster.cluster.stream()
                                .map(cw -> cw.clone)
                                .map(clone -> {
                                            List<Double> calculatedFormula = cluster.cluster.stream()
                                                    .map(cw -> cw.clone)
                                                    .map(compareWith -> fitnessFunction(fitnessFunctionParams(clone, compareWith)))
                                                    .collect(Collectors.toList());
                                            return String.format("%6d|%.5f|%s",
                                                    clone.getId(),
                                                    calculatedFormula.stream().filter(it -> it > 0.0).mapToDouble(it -> it).min().orElseThrow(IllegalArgumentException::new),
                                                    calculatedFormula.stream().map(r -> String.format("%.5f", r)).collect(Collectors.joining("|"))
                                            );
                                        }
                                )
                                .collect(Collectors.joining("\n"))
                        );
                        System.out.println("\n");

                        System.out.println(cluster.cluster.stream()
                                .map(cw -> cw.clone)
                                .map(clone -> {
                                            List<Double> calculatedFormula = cluster.cluster.stream()
                                                    .map(cw -> cw.clone)
                                                    .map(compareWith -> fitnessFunction(fitnessFunctionParams(clone, compareWith)))
                                                    .collect(Collectors.toList());
                                            return String.format("%6d|%.5f|%s",
                                                    clone.getId(),
                                                    calculatedFormula.stream().filter(it -> it > 0.0).mapToDouble(it -> it).min().orElseThrow(IllegalArgumentException::new),
                                                    calculatedFormula.stream().map(r -> String.format("%.5f", r)).collect(Collectors.joining("|"))
                                            );
                                        }
                                )
                                .collect(Collectors.joining("\n"))
                        );
                        System.out.println("\n");
                    }


//                    Cluster<CloneWrapper> clusterToProcess = new Cluster<>(cluster.cluster.stream()
//                            .filter(it -> clonesDefinitelyInTree.contains(it.clone.getId()) || clonesMaybeInTree.contains(it.clone.getId()))
//                            .collect(Collectors.toList()));
                    Cluster<CloneWrapper> clusterToProcess = cluster;
                    List<Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>> trees = shmTreeBuilder.processCluster(clusterToProcess)
                            .stream()
                            .sorted(Comparator.<Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>>comparingLong(tree -> tree.allNodes().count()).reversed())
                            .collect(Collectors.toList());

                    System.out.println(trees.stream().map(idPrinter::print).collect(Collectors.joining("\n")));
                    System.out.println("\n");

                    List<Tree<Pair<String, NucleotideSequence>>> mappedTrees = trees.stream()
                            .map(tree -> tree.map(this::idPair))
                            .sorted(Comparator.<Tree<Pair<String, NucleotideSequence>>, Long>comparing(tree -> tree.allNodes().count()).reversed())
                            .collect(Collectors.toList());
                    XmlTreePrinter<Pair<String, NucleotideSequence>> xmlTreePrinter = new XmlTreePrinter<>(
                            node -> node.getContent().getFirst() + "(" + md5(node.getContent().getSecond()) + ")"
                    );

                    boolean printAllSequences = false;
                    if (printAllSequences) {
                        trees.stream()
                                .flatMap(Tree::allNodes)
                                .map(node -> {
                                    NucleotideSequence sequence = getSequence(node.getContent());
                                    NucleotideSequence CDR3 = getCDR3(node.getContent());
                                    return String.format("%20s | %50s | %s",
                                            md5(sequence),
                                            CDR3.toString(),
                                            sequence);
                                })
                                .distinct()
                                .forEach(System.out::println);
                        System.out.println();
                    }

                    System.out.println(mappedTrees.stream().map(xmlTreePrinter::print).collect(Collectors.joining("\n")));
                    System.out.println();

                    System.out.println(mappedTrees.stream().map(xmlTreePrinter::print).map(this::md5).collect(Collectors.joining("\n")));
                    System.out.println("\n");


                    for (Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree : trees) {
                        XmlTreePrinter<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> printerWithBreadcrumbs = new XmlTreePrinter<>(
                                node -> {
                                    Pair<String, NucleotideSequence> idPair = idPair(node.getContent());

                                    if (node.getParent() == null) {
                                        return "" + md5(idPair.getSecond());
                                    }

                                    List<Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>> ancestors = Stream.concat(
                                                    allAncestors(node),
                                                    Stream.of(node)
                                            )
                                            .filter(it -> it != tree.getRoot())
                                            .collect(Collectors.toList());

                                    List<Clone> clonesInBranch = ancestors.stream()
                                            .flatMap(Tree.Node::allDescendants)
                                            .distinct()
                                            .map(Tree.Node::getContent)
                                            .map(content -> content.convert(it -> Optional.of(it.clone), it -> Optional.<Clone>empty()))
                                            .flatMap(Java9Util::stream)
                                            .collect(Collectors.toList());
                                    if (clonesInBranch.isEmpty()) {
                                        return "?!!!" + idPair.getFirst() + "|" + md5(idPair.getSecond());
                                    }
                                    int vPositionInCDR3 = findVPositionInCDR3(clonesInBranch);
                                    int jPositionInCDR3 = findJPositionInCDR3(clonesInBranch);
                                    Range NDNRange = new Range(vPositionInCDR3, jPositionInCDR3);
//                                    Range NDNRange = new Range(0, 0);

                                    NucleotideSequence CDR3OfParent = getCDR3(node.getParent().getContent()).getRange(NDNRange);
                                    NucleotideSequence CDR3OfNode = getCDR3(node.getContent()).getRange(NDNRange);

                                    Mutations<NucleotideSequence> mutationsOfNDN = Aligner.alignGlobal(
                                            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                            CDR3OfParent,
                                            CDR3OfNode
                                    ).getAbsoluteMutations();

                                    Mutations<NucleotideSequence> mutationsOfCDR3 = Aligner.alignGlobal(
                                            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                            getCDR3(node.getParent().getContent()),
                                            getCDR3(node.getContent())
                                    ).getAbsoluteMutations();

                                    Mutations<NucleotideSequence> mutationsOfVWithoutCDR3 = Aligner.alignGlobal(
                                            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                            getVWithoutCDR3(node.getParent()),
                                            getVWithoutCDR3(node)
                                    ).getAbsoluteMutations();

                                    Mutations<NucleotideSequence> mutationsOfJWithoutCDR3 = Aligner.alignGlobal(
                                            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                            getJWithoutCDR3(node.getParent()),
                                            getJWithoutCDR3(node)
                                    ).getAbsoluteMutations();

                                    return mutationsOfNDN.size() + " :" + idPair.getFirst() + "|" + md5(idPair.getSecond()) + "(" + NDNRange.getLower() + "-" + NDNRange.getUpper() + ")" + " " + mutationsOfNDN + "(" + (mutationsOfNDN.size() / (double) NDNRange.length()) + ") CDR3: " + mutationsOfCDR3.size() + " V: " + mutationsOfVWithoutCDR3.size() + " J: " + mutationsOfJWithoutCDR3.size();
                                }
                        );
                        System.out.println(printerWithBreadcrumbs.print(tree));
                    }
                    System.out.println();


                    if (false) {
                        if (mappedTrees.size() > 1) {
                            Tree<Pair<String, NucleotideSequence>> bigTree = mappedTrees.get(0);

                            XmlTreePrinter<Pair<String, NucleotideSequence>> printerForSmallTree = new XmlTreePrinter<>(
                                    nodeOfSmallTree -> nodeOfSmallTree.getContent().getFirst() + ":" + bigTree.allNodes()
                                            .map(nodeOfBigTree -> Pair.create(nodeOfBigTree, Aligner.alignGlobal(
                                                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                                    nodeOfBigTree.getContent().getSecond(),
                                                    nodeOfSmallTree.getContent().getSecond()
                                            ).getScore()))
                                            .max(Comparator.comparing(Pair::getSecond))
                                            //                                        .map(it -> it.getSecond().size() + "(" + it.getSecond() + ")" + "|" + it.getFirst().getContent().getSecond().hashCode())
                                            .map(it -> it.getSecond() + "|" + md5(it.getFirst().getContent().getSecond()))
                                            .orElseThrow(IllegalArgumentException::new) + ":" + bigTree.allNodes()
                                            .filter(nodeOfBigTree -> !nodeOfBigTree.getContent().getFirst().equals("?"))
                                            .map(nodeOfBigTree -> Pair.create(nodeOfBigTree, Aligner.alignGlobal(
                                                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                                    nodeOfBigTree.getContent().getSecond(),
                                                    nodeOfSmallTree.getContent().getSecond()
                                            ).getScore()))
                                            .max(Comparator.comparing(Pair::getSecond))
                                            //                                        .map(it -> it.getSecond().size() + "(" + it.getSecond() + ")" + "|" + it.getFirst().getContent().getSecond().hashCode())
                                            .map(it -> it.getSecond() + "|" + md5(it.getFirst().getContent().getSecond()))
                                            .orElseThrow(IllegalArgumentException::new)
                            );
                            mappedTrees.subList(1, mappedTrees.size())
                                    .forEach(smallTree -> System.out.println(printerForSmallTree.print(smallTree)));
                            System.out.println();
                        }
                    }

                    boolean printFasta = false;
                    if (printFasta) {
                        System.out.println("FASTA full:");
                        for (int i = 0; i < mappedTrees.size(); i++) {
                            Tree<Pair<String, NucleotideSequence>> tree = mappedTrees.get(i);
                            System.out.println(i);
                            System.out.println(tree.allNodes()
                                    .map(Tree.Node::getContent)
                                    .map(content -> ">" + content.getFirst() + "|" + md5(content.getSecond()) + "\n" + content.getSecond())
                                    .distinct()
                                    .collect(Collectors.joining("\n")));

                            System.out.println();
                        }

                        System.out.println("FASTA CDR3:");
                        for (int i = 0; i < trees.size(); i++) {
                            Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree = trees.get(i);
                            System.out.println(i);
                            System.out.println(tree.allNodes()
                                    .map(Tree.Node::getContent)
                                    .map(content -> ">" + content.convert(it -> it.clone.getId(), it -> "?") + "|" + md5(getCDR3(content)) + "\n" + getCDR3(content))
                                    .distinct()
                                    .collect(Collectors.joining("\n")));

                            System.out.println();
                        }

                        System.out.println(Lists.newArrayList(
                                        trees.get(0),
                                        trees.get(1),
                                        trees.get(2),
                                        trees.get(4)
                                ).stream()
                                .flatMap(Tree::allNodes)
                                .map(Tree.Node::getContent)
                                .map(content -> content.convert(it -> Optional.of(it.clone), it -> Optional.<Clone>empty()))
                                .flatMap(Java9Util::stream)
                                .map(clone -> ">" + clone.getId() + "\n" + clone.getTarget(0).getSequence())
                                .collect(Collectors.joining("\n")));
                        System.out.println();

                        AncestorInfo ancestorInfo = trees.get(0).allNodes()
                                .map(node -> node.getContent().convert(it -> Optional.<AncestorInfo>empty(), Optional::of))
                                .flatMap(Java9Util::stream)
                                .findFirst()
                                .orElseThrow(IllegalArgumentException::new);
                        System.out.println("CDR3 range" + ancestorInfo.getCDR3Begin() + "-" + ancestorInfo.getCDR3End());
                        System.out.println();
                    }


                    Set<Integer> clonesInTree = trees.stream()
                            .flatMap(Tree::allNodes)
                            .map(node -> node.getContent().convert(it -> Optional.of(it.clone.getId()), it -> Optional.<Integer>empty()))
                            .flatMap(Java9Util::stream)
                            .collect(Collectors.toSet());

                    long clonesNotInTreesCount = cluster.cluster.stream().map(it -> it.clone.getId()).filter(it -> !clonesInTree.contains(it)).count();
                    System.out.println("not in trees: " + clonesNotInTreesCount);
                    System.out.println();

                    System.out.println("ids:");
                    System.out.println();
                    System.out.println(cluster.cluster.stream().map(it -> String.valueOf(it.clone.getId())).collect(Collectors.joining("|")));

                    System.out.println("\n\n");
                }
            }
        }
    }

    private NucleotideSequence getSequence(ObservedOrReconstructed<CloneWrapper, AncestorInfo> content) {
        return content.convert(
                cloneWrapper -> cloneWrapper.clone.getTarget(0).getSequence(),
                AncestorInfo::getSequence
        );
    }

    private String md5(NucleotideSequence sequence) {
        return ExceptionUtil.wrap(() -> {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            for (int i = 0; i < sequence.size(); i++) {
                md5.update(sequence.codeAt(i));
            }
            return new String(Base64.getEncoder().encode(md5.digest()));
        }).get();
    }

    private String md5(String sequence) {
        return ExceptionUtil.wrap(() -> {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(sequence.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getEncoder().encode(md5.digest()));
        }).get();
    }

    private Pair<String, NucleotideSequence> idPair(ObservedOrReconstructed<CloneWrapper, AncestorInfo> content) {
        return Pair.create(
                content.convert(
                        cloneWrapper -> String.valueOf(cloneWrapper.clone.getId()),
                        seq -> "?"
                ),
                getSequence(content)
        );
    }

    private NucleotideSequence getCDR3(ObservedOrReconstructed<CloneWrapper, AncestorInfo> content) {
        return content.convert(
                cloneWrapper -> cloneWrapper.clone.getNFeature(GeneFeature.CDR3),
                ancestorInfo -> ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End())
        );
    }

    private NucleotideSequence getVWithoutCDR3(Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node) {
        return node.getContent().convert(
                cloneWrapper -> cloneWrapper.clone.getNFeature(new GeneFeature(FR2Begin, CDR3Begin)),
                ancestorInfo -> ancestorInfo.getSequence().getRange(0, ancestorInfo.getCDR3Begin())
        );
    }

    private NucleotideSequence getJWithoutCDR3(Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node) {
        return node.getContent().convert(
                cloneWrapper -> cloneWrapper.clone.getNFeature(new GeneFeature(CDR3End, FR4End)),
                ancestorInfo -> ancestorInfo.getSequence().getRange(0, ancestorInfo.getCDR3Begin())
        );
    }

    private int findVPositionInCDR3(List<Clone> clones) {
        return clones.stream()
                .map(clone -> clone.getBestHit(Variable).getPartitioningForTarget(0).getLength(new GeneFeature(CDR3Begin, VEndTrimmed)))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private int findJPositionInCDR3(List<Clone> clones) {
        return clones.stream()
                .map(clone -> clone.getNFeature(GeneFeature.CDR3).size() - clone.getBestHit(GeneType.Joining).getPartitioningForTarget(0).getLength(new GeneFeature(JBeginTrimmed, CDR3End)))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private <T> Stream<Tree.Node<T>> allAncestors(Tree.Node<T> node) {
        return Optional.ofNullable(node.getParent())
                .map(parent -> Stream.concat(
                        Stream.of(parent),
                        allAncestors(parent)
                ))
                .orElse(Stream.empty());
    }

    private double fitnessFunction(FitnessFunctionParams params) {
        return fitnessFunctionFirstPart(params) + fitnessFunctionSecondPart(params) + fitnessFunctionThirdPart(params);
    }

    private double fitnessFunctionFirstPart(FitnessFunctionParams params) {
        return 0;
//        return 100 * Math.pow(params.distanceBetweenClonesInCDR3, 2.0) * Math.pow(params.maxScoreToGermline, 5.0);
    }

    private double fitnessFunctionSecondPart(FitnessFunctionParams params) {
        return Math.pow(params.distanceBetweenClonesWithoutCDR3, 1.0);
    }

    private double fitnessFunctionThirdPart(FitnessFunctionParams params) {
        return 4 * Math.pow(params.distanceBetweenClonesInCDR3, 1.0) * Math.pow(params.minDistanceToGermline - 1, 6.0);
    }

    private FitnessFunctionParams fitnessFunctionParams(Clone first, Clone second) {
        List<MutationsWithRange> VMutationsOfFirst = getMutationsWithoutCDR3(first, Variable);
        List<MutationsWithRange> VMutationsOfSecond = getMutationsWithoutCDR3(second, Variable);

        List<MutationsWithRange> VMutationsBetween = mutationsBetween(VMutationsOfFirst, VMutationsOfSecond);
        double VMutationsBetweenScore = score(VMutationsBetween);

        List<MutationsWithRange> JMutationsOfFirst = getMutationsWithoutCDR3(first, Joining);
        List<MutationsWithRange> JMutationsOfSecond = getMutationsWithoutCDR3(second, Joining);

        List<MutationsWithRange> JMutationsBetween = mutationsBetween(JMutationsOfFirst, JMutationsOfSecond);
        double JMutationsBetweenScore = score(JMutationsBetween);

        double CDR3MutationsBetweenScore = alignmentOfCDR3(first, second).getScore();

        double maxScoreForFirstVJ = maxScore(VMutationsOfFirst) + maxScore(JMutationsOfFirst);
        double maxScoreForSecondVJ = maxScore(VMutationsOfSecond) + maxScore(JMutationsOfSecond);
        double maxScoreForVJ = Math.max(maxScoreForFirstVJ, maxScoreForSecondVJ);
        double maxScoreForFirstCDR3 = maxScoreForCDR3(first);
        double maxScoreForSecondCDR3 = maxScoreForCDR3(second);
        double maxScoreForCDR3 = Math.max(maxScoreForFirstCDR3, maxScoreForSecondCDR3);


        double normalizedDistanceFromFirstToGermline = 1 - (score(VMutationsOfFirst) + score(JMutationsOfFirst)) / maxScoreForFirstVJ;
        double normalizedDistanceFromSecondToGermline = 1 - (score(VMutationsOfSecond) + score(JMutationsOfSecond)) / maxScoreForSecondVJ;
        double normalizedDistanceBetweenClones = 1 - (VMutationsBetweenScore + JMutationsBetweenScore + CDR3MutationsBetweenScore) /
                (maxScoreForVJ + maxScoreForCDR3);
        double normalizedDistanceBetweenClonesInCDR3 = 1 - (CDR3MutationsBetweenScore) / maxScoreForCDR3;
        double normalizedDistanceBetweenClonesWithoutCDR3 = 1 - (VMutationsBetweenScore + JMutationsBetweenScore) / (maxScoreForVJ + maxScoreForCDR3);

        return new FitnessFunctionParams(
                normalizedDistanceBetweenClonesInCDR3,
                normalizedDistanceBetweenClones,
                normalizedDistanceBetweenClonesWithoutCDR3,
                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline)
        );
    }

    private double score(List<MutationsWithRange> mutationsWithRanges) {
        return mutationsWithRanges.stream()
                .mapToDouble(mutations -> AlignmentUtils.calculateScore(
                        mutations.getFromBaseToParent().mutate(mutations.getSequence1()),
                        mutations.getFromParentToThis(),
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring()
                ))
                .sum();
    }

    private double maxScore(List<MutationsWithRange> vMutationsBetween) {
        return vMutationsBetween.stream()
                .mapToDouble(mutations -> AlignmentUtils.calculateScore(
                        mutations.getFromBaseToParent().mutate(mutations.getSequence1()),
                        Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring()
                ))
                .sum();
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

    private Alignment<NucleotideSequence> alignmentOfCDR3(Clone clone, Clone compareWith) {
        return Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                clone.getNFeature(GeneFeature.CDR3),
                compareWith.getNFeature(GeneFeature.CDR3)
        );
    }

    private double maxScoreForCDR3(Clone clone) {
        return AlignmentUtils.calculateScore(
                clone.getNFeature(GeneFeature.CDR3),
                Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        );
    }

    private Mutations<NucleotideSequence> mutationsBetweenWithoutCDR3(Clone first, Clone second, GeneType geneType) {
        Mutations<NucleotideSequence> firstMutations = getAbsoluteMutationsWithoutCDR3(first, geneType);
        Mutations<NucleotideSequence> secondMutations = getAbsoluteMutationsWithoutCDR3(second, geneType);

        return firstMutations.invert().combineWith(secondMutations);
    }

    private List<MutationsWithRange> mutationsBetween(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return firstMutations.stream()
                .flatMap(base -> secondMutations.stream().flatMap(comparison -> {
                    Range intersection = base.getSequence1Range().intersection(comparison.getSequence1Range());
                    if (!intersection.isEmpty()) {
                        return Stream.of(new MutationsWithRange(
                                base.getSequence1(),
                                base.getCombinedMutations(),
                                base.getCombinedMutations().invert().combineWith(comparison.getCombinedMutations()),
                                intersection
                        ));
                    } else {
                        return Stream.empty();
                    }
                }))
                .collect(Collectors.toList());
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
                .map(clone -> alignmentOfCDR3(root, clone).getAbsoluteMutations())
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
        Mutations<NucleotideSequence> mutations = alignmentOfCDR3(root, clone).getAbsoluteMutations();
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

    private static int numberOfMutationsWithoutCDR3(Clone clone, GeneType geneType) {
        return getAbsoluteMutationsWithoutCDR3(clone, geneType).size();
    }

    private static Mutations<NucleotideSequence> getAbsoluteMutationsWithoutCDR3(Clone clone, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);


        int[] filteredMutations = IntStream.range(0, bestHit.getAlignments().length)
                .flatMap(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Range CDR3Range = CDR3Sequence1Range(bestHit, index);

                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    return IntStream.range(0, mutations.size())
                            .map(mutations::getMutation)
                            .filter(mutation -> !CDR3Range.contains(Mutation.getPosition(mutation)));
                })
                .toArray();

        return new MutationsBuilder<>(NucleotideSequence.ALPHABET, false, filteredMutations, filteredMutations.length).createAndDestroy();
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
