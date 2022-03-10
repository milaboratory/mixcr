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

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.*;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.Wildcard;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.trees.*;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.mixcr.util.RangeInfo;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
import static com.milaboratory.mixcr.util.ClonesAlignmentRanges.CDR3Sequence1Range;
import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.*;

@CommandLine.Command(name = CommandBuildSHMTree.BUILD_SHM_TREE_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Builds SHM trees.")
public class CommandBuildSHMTree extends ACommandWithOutputMiXCR {
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

    public List<String> getClnsFiles() {
        return getInputFiles();
    }

    @CommandLine.Option(description = "SHM tree builder parameters preset.",
            names = {"-p", "--preset"})
    public String shmTreeBuilderParametersName = "default";

    private SHMTreeBuilderParameters shmTreeBuilderParameters = null;

    private void ensureParametersInitialized() {
        if (shmTreeBuilderParameters != null)
            return;

        shmTreeBuilderParameters = SHMTreeBuilderParametersPresets.getByName(shmTreeBuilderParametersName);
        if (shmTreeBuilderParameters == null)
            throwValidationException("Unknown parameters: " + shmTreeBuilderParametersName);
    }

    @Override
    public void run0() throws Exception {
        ensureParametersInitialized();
        List<CloneReader> cloneReaders = getClnsFiles().stream()
                .map(ExceptionUtil.wrap(path -> CloneSetIO.mkReader(Paths.get(path), VDJCLibraryRegistry.getDefault())))
                .collect(Collectors.toList());
        if (cloneReaders.size() == 0) {
            throw new IllegalArgumentException("there is no files to process");
        }
        if (cloneReaders.stream().map(CloneReader::getAssemblerParameters).distinct().count() != 1) {
            throw new IllegalArgumentException("input files must have the same assembler parameters");
        }
        SHMTreeBuilder shmTreeBuilder = new SHMTreeBuilder(
                shmTreeBuilderParameters,
                new ClusteringCriteria.DefaultClusteringCriteria(),
                cloneReaders
        );
        for (Cluster<CloneWrapper> cluster : CUtils.it(shmTreeBuilder.buildClusters(shmTreeBuilder.sortClonotypes()))) {
            int rootCloneId = 24722;
            if (cluster.cluster.stream().map(it -> it.clone.getId()).anyMatch(it -> it == rootCloneId)) {
                shmTreeBuilder.zeroStep(cluster);
            }
        }
        shmTreeBuilder.makeDecisions("Building initial clusters");
        //TODO check that all trees has minimum common mutations in VJ

        for (String step : shmTreeBuilderParameters.stepsOrder) {
            for (Cluster<CloneWrapper> cluster : CUtils.it(shmTreeBuilder.buildClusters(shmTreeBuilder.sortClonotypes()))) {
                int rootCloneId = 24722;
                if (cluster.cluster.stream().map(it -> it.clone.getId()).anyMatch(it -> it == rootCloneId)) {
                    shmTreeBuilder.applyStep(cluster, step);
                }
            }
            shmTreeBuilder.makeDecisions(step);
        }

        List<TreeWithMeta> trees = new ArrayList<>();
        for (Cluster<CloneWrapper> cluster : CUtils.it(shmTreeBuilder.buildClusters(shmTreeBuilder.sortClonotypes()))) {
            int rootCloneId = 24722;
            if (cluster.cluster.stream().map(it -> it.clone.getId()).anyMatch(it -> it == rootCloneId)) {
                trees.addAll(shmTreeBuilder.getResult(cluster));
            }
        }

        trees = trees.stream()
                .sorted(Comparator.<TreeWithMeta>comparingLong(tree -> tree.getTree().allNodes().count()).reversed())
                .collect(Collectors.toList());

        for (TreeWithMeta treeWithMeta : trees) {
            treeWithMeta.getTree().allNodes().forEach(nodeWithParent -> {
                Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node = nodeWithParent.getNode();
                Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> parent = nodeWithParent.getParent();
                Optional<Clone> clone = node.getContent().convert(it -> Optional.of(it.clone), it -> Optional.empty());
                if (clone.isPresent() && parent != null) {
                    if (!getSequence(parent.getContent()).equals(clone.get().getTarget(0).getSequence())) {
                        throw new IllegalStateException();
                    }
                }
            });
        }

        List<Tree<Pair<String, NucleotideSequence>>> mappedTrees = trees.stream()
                .map(treeWithMeta -> treeWithMeta.getTree().map(this::idPair))
                .sorted(Comparator.<Tree<Pair<String, NucleotideSequence>>, Long>comparing(tree -> tree.allNodes().count()).reversed())
                .collect(Collectors.toList());
        XmlTreePrinter<Pair<String, NucleotideSequence>> xmlTreePrinter = new XmlTreePrinter<>(
                nodeWithParent -> nodeWithParent.getNode().getContent().getFirst() + "(" + md5(nodeWithParent.getNode().getContent().getSecond()) + ")"
        );

        System.out.println(mappedTrees.stream().map(xmlTreePrinter::print).map(this::md5).collect(Collectors.joining("\n")));
        System.out.println("\n");

        for (TreeWithMeta treeWithMeta : trees) {
            Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree = treeWithMeta.getTree();
            XmlTreePrinter<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> printerOfNDN = new XmlTreePrinter<>(
                    nodeWithParent -> {
                        Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node = nodeWithParent.getNode();
                        Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> parent = nodeWithParent.getParent();
                        Pair<String, NucleotideSequence> idPair = idPair(node.getContent());

                        if (parent == null) {
                            return "" + md5(idPair.getSecond());
                        }

                        NucleotideSequence CDR3OfNode = getCDR3(node.getContent());

                        RootInfo rootInfo = treeWithMeta.getRootInfo();
                        Range NDNRange = new Range(rootInfo.getVRangeInCDR3().length(), CDR3OfNode.size() - rootInfo.getJRangeInCDR3().length());

                        Mutations<NucleotideSequence> mutationsOfNDN = Aligner.alignGlobal(
                                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                getCDR3(parent.getContent()).getRange(NDNRange),
                                getCDR3(node.getContent()).getRange(NDNRange)
                        ).getAbsoluteMutations();

                        Mutations<NucleotideSequence> mutationsOfV = Aligner.alignGlobal(
                                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                getV(parent, treeWithMeta.getRootInfo()),
                                getV(node, treeWithMeta.getRootInfo())
                        ).getAbsoluteMutations().move(232);

                        Mutations<NucleotideSequence> mutationsOfJWithoutCDR3 = Aligner.alignGlobal(
                                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                getJ(parent, treeWithMeta.getRootInfo()),
                                getJ(node, treeWithMeta.getRootInfo())
                        ).getAbsoluteMutations().move(11);

                        NucleotideSequence NDN = CDR3OfNode.getRange(NDNRange);
                        int wildcardsScore = 0;
                        for (int i = 0; i < NDN.size(); i++) {
                            Wildcard wildcard = NucleotideSequence.ALPHABET.codeToWildcard(NDN.codeAt(i));
                            wildcardsScore += wildcard.basicSize();
                        }
                        return NDN + " (" + String.format("%.2f", wildcardsScore / (double) NDN.size()) + ")" + ":" + idPair.getFirst() + " V: " + mutationsOfV.size() + " J: " + mutationsOfJWithoutCDR3.size() + " V: " + mutationsOfV + " J: " + mutationsOfJWithoutCDR3 + " NDN: " + mutationsOfNDN;
                    }
            );
            System.out.println();
            long count = treeWithMeta.getTree().allNodes()
                    .filter(node -> node.getNode().getContent().convert(it -> true, it -> false))
                    .count();
            System.out.println(treeWithMeta.getVJBase() + " size: " + count);
            System.out.println(printerOfNDN.print(tree));
        }
        System.out.println();

        System.out.println("threshold: " + shmTreeBuilderParameters.thresholdForFreeClones);
        System.out.println("NDNScoreMultiplier: " + shmTreeBuilderParameters.NDNScoreMultiplier);
        System.out.println();

        System.out.println("\n\n");
    }

    private Range minVRangeInCDR3(Cluster<CloneWrapper> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.clone)
                .map(this::VRangeInCDR3)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private Range minJRangeInCDR3(Cluster<CloneWrapper> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.clone)
                .map(this::JRangeInCDR3)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private Range VRangeInCDR3(Clone clone) {
        VDJCHit bestHit = clone.getBestHit(Variable);
        int positionOfCDR3Begin = bestHit.getGene().getPartitioning().getRelativePosition(bestHit.getAlignedFeature(), CDR3Begin);
        return new Range(
                positionOfCDR3Begin,
                positionOfCDR3Begin + clone.getNFeature(new GeneFeature(CDR3Begin, VEndTrimmed)).size()
        );
    }

    private Range JRangeInCDR3(Clone clone) {
        VDJCHit bestHit = clone.getBestHit(Joining);
        int positionOfCDR3End = bestHit.getGene().getPartitioning().getRelativePosition(bestHit.getAlignedFeature(), CDR3End);
        return new Range(
                positionOfCDR3End - clone.getNFeature(new GeneFeature(JBeginTrimmed, CDR3End)).size(),
                positionOfCDR3End
        );
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

    private NucleotideSequence getV(Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node, RootInfo rootInfo) {
        return node.getContent().convert(
                cloneWrapper -> cloneWrapper.clone.getNFeature(new GeneFeature(FR1End, CDR3Begin))
                        .concatenate(cloneWrapper.clone.getNFeature(CDR3).getRange(0, rootInfo.getVRangeInCDR3().length())),
                ancestorInfo -> ancestorInfo.getSequence().getRange(0, ancestorInfo.getCDR3Begin() + rootInfo.getVRangeInCDR3().length())
        );
    }

    private NucleotideSequence getJ(Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node, RootInfo rootInfo) {
        return node.getContent().convert(
                cloneWrapper -> {
                    NucleotideSequence CDR3 = cloneWrapper.clone.getNFeature(GeneFeature.CDR3);
                    return CDR3.getRange(CDR3.size() - rootInfo.getJRangeInCDR3().length(), CDR3.size())
                            .concatenate(cloneWrapper.clone.getNFeature(new GeneFeature(CDR3End, FR4End)));
                },
                ancestorInfo -> ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3End() - rootInfo.getJRangeInCDR3().length(), ancestorInfo.getSequence().size())
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

    private <T> Stream<Tree.Node<T>> allAncestors(Tree<T> tree, Tree.NodeWithParent<T> node) {
        return Optional.ofNullable(node.getParent())
                .map(parent -> Stream.concat(
                        Stream.of(parent),
                        allAncestors(tree, tree.allNodes().filter(it -> it.getNode() == node.getParent()).findFirst().orElseThrow(IllegalStateException::new))
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
        return Math.pow(params.distanceBetweenClonesWithoutNDN, 1.0);
    }

    private double fitnessFunctionThirdPart(FitnessFunctionParams params) {
        return 4 * Math.pow(params.distanceBetweenClonesInNDN, 1.0) * Math.pow(params.minDistanceToGermline - 1, 6.0);
    }

    private FitnessFunctionParams fitnessFunctionParams(Clone first, Clone second, Range VRangeInCDR3, Range JRangeInCDR3, AlignmentScoring<NucleotideSequence> VScoring, AlignmentScoring<NucleotideSequence> JScoring) {
        List<MutationsWithRange> VMutationsOfFirst = getMutationsWithoutCDR3(first, Variable);
        List<MutationsWithRange> VMutationsOfSecond = getMutationsWithoutCDR3(second, Variable);
        if (!VRangeInCDR3.isEmpty()) {
            VMutationsOfFirst.add(VMutationsInCDR3(first, VRangeInCDR3));
            VMutationsOfSecond.add(VMutationsInCDR3(second, VRangeInCDR3));
        }

        List<MutationsWithRange> VMutationsBetween = mutationsBetween(VMutationsOfFirst, VMutationsOfSecond);
        double VMutationsBetweenScore = score(VMutationsBetween, VScoring);

        List<MutationsWithRange> JMutationsOfFirst = getMutationsWithoutCDR3(first, Joining);
        List<MutationsWithRange> JMutationsOfSecond = getMutationsWithoutCDR3(second, Joining);
        if (!JRangeInCDR3.isEmpty()) {
            JMutationsOfFirst.add(JMutationsInCDR3(first, JRangeInCDR3));
            JMutationsOfSecond.add(JMutationsInCDR3(second, JRangeInCDR3));
        }

        List<MutationsWithRange> JMutationsBetween = mutationsBetween(JMutationsOfFirst, JMutationsOfSecond);
        double JMutationsBetweenScore = score(JMutationsBetween, JScoring);

        NucleotideSequence CDR3OfFirst = first.getNFeature(CDR3);
        NucleotideSequence CDR3OfSecond = second.getNFeature(CDR3);
        AlignmentScoring<NucleotideSequence> NDNScoring = AffineGapAlignmentScoring.getNucleotideBLASTScoring();
        NucleotideSequence NDNOfFirst = CDR3OfFirst.getRange(VRangeInCDR3.length(), CDR3OfFirst.size() - JRangeInCDR3.length());
        NucleotideSequence NDNOfSecond = CDR3OfSecond.getRange(VRangeInCDR3.length(), CDR3OfSecond.size() - JRangeInCDR3.length());
        double NDNMutationsBetweenScore = Aligner.alignGlobal(NDNScoring, NDNOfFirst, NDNOfSecond).getScore();
        double maxScoreForFirstNDN = AlignmentUtils.calculateScore(NDNOfFirst, EMPTY_NUCLEOTIDE_MUTATIONS, NDNScoring);
        double maxScoreForSecondNDN = AlignmentUtils.calculateScore(NDNOfFirst, EMPTY_NUCLEOTIDE_MUTATIONS, NDNScoring);
        double maxScoreForNDN = Math.max(maxScoreForFirstNDN, maxScoreForSecondNDN);
        double averageLengthOfNDN = (NDNOfFirst.size() + NDNOfSecond.size()) / 2.0;

        double scoreForFirstVJ = score(VMutationsOfFirst, VScoring) + score(JMutationsOfFirst, JScoring);
        double scoreForSecondVJ = score(VMutationsOfSecond, VScoring) + score(JMutationsOfSecond, JScoring);

        double maxScoreForFirstVJ = maxScore(VMutationsOfFirst, VScoring) + maxScore(JMutationsOfFirst, JScoring);
        double maxScoreForSecondVJ = maxScore(VMutationsOfSecond, VScoring) + maxScore(JMutationsOfSecond, JScoring);

        double lengthOfFirstVJ = totalLength(VMutationsOfFirst) + totalLength(JMutationsOfFirst);
        double lengthOfSecondVJ = totalLength(VMutationsOfSecond) + totalLength(JMutationsOfSecond);
        double averageLengthOfVJ = (lengthOfFirstVJ + lengthOfSecondVJ) / 2;


        double VMutationsBetweenMaxScore = Math.max(maxScore(VMutationsBetween, VScoring), maxScore(invert(VMutationsBetween), VScoring));
        double JMutationsBetweenMaxScore = Math.max(maxScore(JMutationsBetween, JScoring), maxScore(invert(JMutationsBetween), JScoring));
        double VJMutationsBetweenMaxScore = VMutationsBetweenMaxScore + JMutationsBetweenMaxScore;

        double normalizedDistanceFromFirstToGermline = (maxScoreForFirstVJ - scoreForFirstVJ) / lengthOfFirstVJ;
        double normalizedDistanceFromSecondToGermline = (maxScoreForSecondVJ - scoreForSecondVJ) / lengthOfSecondVJ;
        double normalizedDistanceBetweenClonesInNDN = (maxScoreForNDN - NDNMutationsBetweenScore) / averageLengthOfNDN;
        double normalizedDistanceBetweenClonesWithoutNDN = (VJMutationsBetweenMaxScore - VMutationsBetweenScore - JMutationsBetweenScore) / averageLengthOfVJ;
        double normalizedDistanceBetweenClones = (maxScoreForNDN + VJMutationsBetweenMaxScore - NDNMutationsBetweenScore - VMutationsBetweenScore - JMutationsBetweenScore) / (averageLengthOfNDN + averageLengthOfVJ);

        return new FitnessFunctionParams(
                normalizedDistanceBetweenClonesInNDN,
                normalizedDistanceBetweenClones,
                normalizedDistanceBetweenClonesWithoutNDN,
                normalizedDistanceFromFirstToGermline,
                normalizedDistanceFromSecondToGermline,
                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline)
        );
    }

    private int totalLength(List<MutationsWithRange> mutations) {
        return mutations.stream().mapToInt(it -> it.getRangeInfo().getRange().length()).sum();
    }

    private static List<MutationsWithRange> getMutationsWithoutCDR3(Clone clone, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .flatMap(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Range CDR3Range = CDR3Sequence1Range(bestHit, bestHit.getAlignment(index));
                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    if (CDR3Range == null) {
                        return Stream.of(new MutationsWithRange(
                                alignment.getSequence1(),
                                mutations,
                                new RangeInfo(alignment.getSequence1Range(), false)
                        ));
                    }
                    List<Range> rangesWithoutCDR3 = alignment.getSequence1Range().without(CDR3Range);
                    if (rangesWithoutCDR3.size() > 0) {
                        if (rangesWithoutCDR3.size() > 1) {
                            throw new IllegalStateException();
                        }
                        return Stream.of(new MutationsWithRange(
                                alignment.getSequence1(),
                                mutations,
                                new RangeInfo(rangesWithoutCDR3.get(0), false)
                        ));
                    } else {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }

    private MutationsWithRange VMutationsInCDR3(Clone clone, Range VRangeInCDR3) {
        NucleotideSequence VSequence1 = clone.getBestHit(Variable).getAlignment(0).getSequence1();
        return new MutationsWithRange(
                VSequence1,
                Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        VSequence1,
                        clone.getNFeature(CDR3),
                        VRangeInCDR3.getLower(),
                        VRangeInCDR3.length(),
                        0,
                        VRangeInCDR3.length()
                ).getAbsoluteMutations(),
                new RangeInfo(VRangeInCDR3, false)
        );
    }

    private MutationsWithRange JMutationsInCDR3(Clone clone, Range JRangeInCDR3) {
        NucleotideSequence JSequence1 = clone.getBestHit(Joining).getAlignment(0).getSequence1();
        NucleotideSequence CDR3 = clone.getNFeature(GeneFeature.CDR3);
        return new MutationsWithRange(
                JSequence1,
                Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        JSequence1,
                        CDR3,
                        JRangeInCDR3.getLower(),
                        JRangeInCDR3.length(),
                        CDR3.size() - JRangeInCDR3.length(),
                        JRangeInCDR3.length()
                ).getAbsoluteMutations(),
                new RangeInfo(JRangeInCDR3, false)
        );
    }

    private double score(List<MutationsWithRange> mutationsWithRanges, AlignmentScoring<NucleotideSequence> scoring) {
        return mutationsWithRanges.stream()
                .mapToDouble(mutations -> AlignmentUtils.calculateScore(
                        mutations.getSequence1(),
                        mutations.getMutations(),
                        scoring
                ))
                .sum();
    }

    private double maxScore(List<MutationsWithRange> vMutationsBetween, AlignmentScoring<NucleotideSequence> scoring) {
        return vMutationsBetween.stream()
                .mapToDouble(mutations -> AlignmentUtils.calculateScore(
                        mutations.getSequence1(),
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        scoring
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

    private Mutations<NucleotideSequence> mutationsBetweenWithoutCDR3(Clone first, Clone second, GeneType geneType) {
        Mutations<NucleotideSequence> firstMutations = getAbsoluteMutationsWithoutCDR3(first, geneType);
        Mutations<NucleotideSequence> secondMutations = getAbsoluteMutationsWithoutCDR3(second, geneType);

        return firstMutations.invert().combineWith(secondMutations);
    }

    private List<MutationsWithRange> invert(List<MutationsWithRange> mutations) {
        return mutations.stream()
                .map(it -> new MutationsWithRange(
                        it.getMutations().mutate(it.getSequence1()),
                        it.getMutations().invert(),
                        it.getRangeInfo()
                ))
                .collect(Collectors.toList());
    }

    private List<MutationsWithRange> mutationsBetween(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return firstMutations.stream()
                .flatMap(base -> secondMutations.stream().flatMap(comparison -> {
                    RangeInfo intersection = base.getRangeInfo().intersection(comparison.getRangeInfo());
                    if (intersection != null) {
                        return Stream.of(new MutationsWithRange(
                                base.getSequence1(),
                                base.getMutations().invert().combineWith(comparison.getMutations()),
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
                    Range CDR3Range = CDR3Sequence1Range(bestHit, bestHit.getAlignment(index));

                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    return IntStream.range(0, mutations.size())
                            .map(mutations::getMutation)
                            .filter(mutation -> CDR3Range == null || !CDR3Range.contains(Mutation.getPosition(mutation)));
                })
                .toArray();

        return new MutationsBuilder<>(NucleotideSequence.ALPHABET, false, filteredMutations, filteredMutations.length).createAndDestroy();
    }
}
