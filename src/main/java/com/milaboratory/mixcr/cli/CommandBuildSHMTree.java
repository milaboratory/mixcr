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
import com.milaboratory.mixcr.util.Java9Util;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.math3.util.Pair;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
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

        NewickTreePrinter<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> idPrinter = new NewickTreePrinter<>(
                node -> node.getContent().convert(c -> String.valueOf(c.clone.getId()), it -> ""),
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

                    if (true) {
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

                        Set<Integer> clonesDefinitelyInTree = Sets.newHashSet(
                                24722,
                                5113,
                                4689,
                                36487,
                                13552,
                                39832,
                                22831,
                                29919,
                                49407,
                                48367,
                                20908,
                                20946,
                                29944,
                                25712,
                                15729,
                                21841,
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
                                46060
                        );

                        Set<Integer> clonesMaybeInTree = Sets.newHashSet(
                                19129,
                                41109,
                                37766,
                                15694
                        );

                        Predicate<Clone> clonesForComparisonFilter = it -> clonesDefinitelyInTree.contains(it.getId());
                        boolean skipMismatch = false;

                        List<Pair<Clone, Pair<Pair<FitnessFunctionParams, Clone>, List<Integer>>>> cloneComparisonParams = cluster.cluster.stream()
                                .map(cw -> cw.clone)
                                .map(clone -> {
                                            Pair<FitnessFunctionParams, Clone> minFitnessFunctionParams = cluster.cluster.stream()
                                                    .map(cw -> cw.clone)
                                                    .filter(clonesForComparisonFilter)
                                                    .map(compareWith -> Pair.create(fitnessFunctionParams(clone, compareWith), compareWith))
                                                    .filter(it -> fitnessFunction(it.getFirst()) > 0.0)
                                                    .min(Comparator.comparing(it -> fitnessFunction(it.getFirst()))).orElseThrow(IllegalArgumentException::new);

                                            List<Integer> minMutations = cluster.cluster.stream()
                                                    .map(cw -> cw.clone)
                                                    .filter(clonesForComparisonFilter)
                                                    .map(compareWith -> {
                                                        int vMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Variable).size();
                                                        int jMutationsBetween = mutationsBetweenWithoutCDR3(clone, compareWith, Joining).size();
                                                        int cdr3MutationsBetween = mutationsBetween(clone, compareWith, CDR3).size();
                                                        return Lists.newArrayList(vMutationsBetween, cdr3MutationsBetween, jMutationsBetween);
                                                    })
                                                    .filter(it -> !it.equals(Lists.newArrayList(0, 0, 0)))
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
                            int cdr3MutationsBetween = mutationsBetween(clone, compareWith, CDR3).size();

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

                            String format = String.format("%3d|%6d|%6d|%5s|%6d%s(%.5f)|  %2d|%2d|%2d|  %2d|%2d|%2d|  %2d|  %.5f|%.5f|%.5f|%.5f|%.5f|  %.5f|%.5f|%.5f|%.5f|    %.5f|%.5f",
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
                                    fitnessFunctionParams.commonAncestorDistanceFromGermline,
                                    fitnessFunctionParams.averageDistanceToGermline,
                                    fitnessFunctionParams.areaOfTriangle,
                                    fitnessFunctionParams.radiusOfCircumscribedCircle,
                                    fitnessFunctionParams.baseAngleOfTriangle,
                                    fitnessFunctionParams.baseHeightOfTriangle,
                                    fitnessFunctionFirstPart(fitnessFunctionParams),
                                    fitnessFunctionSecondPart(fitnessFunctionParams)
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
                                            FitnessFunctionParams fitnessFunctionParams = fitnessFunctionParams(clone, compareWith);
                                            return String.format(" %.2f|%.2f|%.2f ",
                                                    fitnessFunctionParams.averageDistanceToGermline * 20,
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


                    Collection<Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>> trees = shmTreeBuilder.processCluster(cluster);

                    System.out.println(trees.stream().map(idPrinter::print).collect(Collectors.joining("\n")));
                    System.out.println("\n");

                    List<Tree<Pair<String, NucleotideSequence>>> mappedTrees = trees.stream()
                            .map(tree -> tree.map(this::idPair))
                            .sorted(Comparator.<Tree<Pair<String, NucleotideSequence>>, Long>comparing(tree -> tree.allNodes().count()).reversed())
                            .collect(Collectors.toList());
                    XmlTreePrinter<Pair<String, NucleotideSequence>> xmlTreePrinter = new XmlTreePrinter<>(
                            node -> node.getContent().getFirst() + "(" + node.getContent().getSecond().hashCode() + ")"
                    );

                    System.out.println(mappedTrees.stream().map(xmlTreePrinter::print).collect(Collectors.joining("\n")));
                    System.out.println();


                    for (Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree : trees) {
                        XmlTreePrinter<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> printerWithBreadcrumbs = new XmlTreePrinter<>(
                                node -> {
                                    Pair<String, NucleotideSequence> idPair = idPair(node.getContent());

                                    if (node.getParent() == null) {
                                        return "" + idPair.getSecond().hashCode();
                                    }

                                    List<Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>> ancestors = allAncestors(node)
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
                                        return "?!!!" + idPair.getFirst() + "|" + idPair.getSecond().hashCode();
                                    }
                                    int vPositionInCDR3 = findVPositionInCDR3(clonesInBranch);
                                    int jPositionInCDR3 = findJPositionInCDR3(clonesInBranch);
                                    Range NDNRange = new Range(vPositionInCDR3, jPositionInCDR3);
//                                    Range NDNRange = new Range(0, 0);

                                    NucleotideSequence CDR3OfParent = getCDR3(node.getParent()).getRange(NDNRange);
                                    NucleotideSequence CDR3OfNode = getCDR3(node).getRange(NDNRange);

                                    Mutations<NucleotideSequence> mutationsOfNDN = Aligner.alignGlobal(
                                            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                            CDR3OfParent,
                                            CDR3OfNode
                                    ).getAbsoluteMutations();

                                    return mutationsOfNDN.size() + " :" + idPair.getFirst() + "|" + idPair.getSecond().hashCode() + "(" + NDNRange.getLower() + "-" + NDNRange.getUpper() + ")" + " " + mutationsOfNDN + "(" + (mutationsOfNDN.size() / (double) NDNRange.length()) + ")";
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
                                            ).getAbsoluteMutations()))
                                            .min(Comparator.comparing(it -> it.getSecond().size()))
                                            //                                        .map(it -> it.getSecond().size() + "(" + it.getSecond() + ")" + "|" + it.getFirst().getContent().getSecond().hashCode())
                                            .map(it -> it.getSecond().size() + "|" + it.getFirst().getContent().getSecond().hashCode())
                                            .orElseThrow(IllegalArgumentException::new) + ":" + bigTree.allNodes()
                                            .filter(nodeOfBigTree -> !nodeOfBigTree.getContent().getFirst().equals("?"))
                                            .map(nodeOfBigTree -> Pair.create(nodeOfBigTree, Aligner.alignGlobal(
                                                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                                    nodeOfBigTree.getContent().getSecond(),
                                                    nodeOfSmallTree.getContent().getSecond()
                                            ).getAbsoluteMutations()))
                                            .min(Comparator.comparing(it -> it.getSecond().size()))
                                            //                                        .map(it -> it.getSecond().size() + "(" + it.getSecond() + ")" + "|" + it.getFirst().getContent().getSecond().hashCode())
                                            .map(it -> it.getSecond().size() + "|" + it.getFirst().getContent().getSecond().hashCode())
                                            .orElseThrow(IllegalArgumentException::new)
                            );
                            mappedTrees.subList(1, mappedTrees.size())
                                    .forEach(smallTree -> System.out.println(printerForSmallTree.print(smallTree)));
                            System.out.println();
                        }
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

    private Pair<String, NucleotideSequence> idPair(ObservedOrReconstructed<CloneWrapper, AncestorInfo> node) {
        return node.convert(
                cloneWrapper -> Pair.create(String.valueOf(cloneWrapper.clone.getId()), cloneWrapper.clone.getTargets()[0].getSequence()),
                seq -> Pair.create("?", seq.getSequence())
        );
    }

    private NucleotideSequence getCDR3(Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node) {
        return node.getContent().convert(
                cloneWrapper -> cloneWrapper.clone.getNFeature(GeneFeature.CDR3),
                ancestorInfo -> ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End())
        );
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

    private int findVPositionInCDR3(List<Clone> clones) {
        return clones.stream()
                .map(clone -> clone.getBestHit(Variable).getPartitioningForTarget(0).getLength(new GeneFeature(CDR3Begin, VEndTrimmed)))
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

    private Double fitnessFunction(FitnessFunctionParams params) {
        return fitnessFunctionFirstPart(params) * fitnessFunctionSecondPart(params);
    }

    private double fitnessFunctionFirstPart(FitnessFunctionParams params) {
        return Math.pow(params.distanceBetweenClonesInCDR3, 1.5);
    }

    private double fitnessFunctionSecondPart(FitnessFunctionParams params) {
        return Math.pow(params.baseAngleOfTriangle + 1, 0.25);
    }

//    private Double fitnessFunction(FitnessFunctionParams params) {
//        return fitnessFunctionFirstPart(params) + fitnessFunctionSecondPart(params);
//    }
//
//    private double fitnessFunctionFirstPart(FitnessFunctionParams params) {
//        return 20 * params.distanceBetweenClonesInCDR3
//                * Math.pow(params.baseAngleOfTriangle + 2, 1.0) /
//                (1 + params.areaOfTriangle);
//    }
//
//    private double fitnessFunctionSecondPart(FitnessFunctionParams params) {
//        return params.distanceBetweenClones
//                * Math.pow(params.radiusOfCircumscribedCircle, 1.0)
//                / (2 + params.areaOfTriangle);
//    }

    private FitnessFunctionParams fitnessFunctionParams(Clone first, Clone second) {
        int vMutationsBetween = mutationsBetweenWithoutCDR3(first, second, Variable).size();
        int jMutationsBetween = mutationsBetweenWithoutCDR3(first, second, Joining).size();
        int cdr3MutationsBetween = mutationsBetween(first, second, CDR3).size();
        int VPlusJLengthOfFirst = ntLengthOfWithoutCDR3(first, Variable) + ntLengthOfWithoutCDR3(first, Joining);
        int VPlusJLengthOfSecond = ntLengthOfWithoutCDR3(second, Variable) + ntLengthOfWithoutCDR3(second, Joining);
        int averageVPlusJLength = (VPlusJLengthOfFirst + VPlusJLengthOfSecond) / 2;
        int averageCDR3Length = (first.ntLengthOf(CDR3) + second.ntLengthOf(CDR3)) / 2;
        double normalizedDistanceFromFirstToGermline = (numberOfMutationsWithoutCDR3(first, Variable) + numberOfMutationsWithoutCDR3(first, Joining)) / (double) VPlusJLengthOfFirst;
        double normalizedDistanceFromSecondToGermline = (numberOfMutationsWithoutCDR3(second, Variable) + numberOfMutationsWithoutCDR3(second, Joining)) / (double) VPlusJLengthOfSecond;
        double normalizedAverageDistanceToGermline = (normalizedDistanceFromFirstToGermline + normalizedDistanceFromSecondToGermline) / 2.0;
        double normalizedCommonAncestorDistanceFromGermline = (intersection(getAbsoluteMutationsWithoutCDR3(first, Variable), getAbsoluteMutationsWithoutCDR3(second, Variable)).size()
                + intersection(getAbsoluteMutationsWithoutCDR3(first, Joining), getAbsoluteMutationsWithoutCDR3(second, Joining)).size()) / (double) averageVPlusJLength;
        double normalizedDistanceBetweenClones = (vMutationsBetween + jMutationsBetween + cdr3MutationsBetween) / (double) (averageVPlusJLength + averageCDR3Length);
        double normalizedDistanceBetweenClonesInCDR3 = (cdr3MutationsBetween) / (double) averageCDR3Length;
        double normalizedDistanceBetweenClonesWithoutCDR3 = (vMutationsBetween + jMutationsBetween) / (double) (averageVPlusJLength);

        double a = (numberOfMutationsWithoutCDR3(first, Variable) + numberOfMutationsWithoutCDR3(first, Joining));
        double b = (numberOfMutationsWithoutCDR3(second, Variable) + numberOfMutationsWithoutCDR3(second, Joining));
        double c = (vMutationsBetween + jMutationsBetween);
        double p = 0.5 * (a + b + c);
        double areaOfTriangle = Math.sqrt(p * (p - a) * (p - b) * (p - c));
        double baseAngleOfTriangle;
        if (a == 0.0 || b == 0.0) {
            baseAngleOfTriangle = 1.0;
        } else {
            baseAngleOfTriangle = (a * a + b * b - c * c) / (2.0 * a * b);
        }
        double baseHeightOfTriangle = areaOfTriangle * 2.0 / c;
        double radiusOfCircumscribedCircle;
        if (areaOfTriangle == 0) {
            radiusOfCircumscribedCircle = Math.max(a, b);
        } else {
            radiusOfCircumscribedCircle = 4 * a * b * c / areaOfTriangle;
        }

        return new FitnessFunctionParams(normalizedDistanceBetweenClonesInCDR3,
                normalizedDistanceBetweenClones,
                normalizedDistanceBetweenClonesWithoutCDR3,
                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline),
                normalizedAverageDistanceToGermline,
                normalizedCommonAncestorDistanceFromGermline,
                areaOfTriangle,
                baseAngleOfTriangle,
                baseHeightOfTriangle,
                radiusOfCircumscribedCircle
        );
    }

    private Mutations<NucleotideSequence> intersection(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Set<Integer> mutationsOfFirstAsSet = Arrays.stream(first.getRAWMutations()).boxed().collect(Collectors.toSet());
        int[] intersection = Arrays.stream(second.getRAWMutations()).filter(mutationsOfFirstAsSet::contains).toArray();
        return new Mutations<>(NucleotideSequence.ALPHABET, intersection);
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
