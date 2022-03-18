package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.Wildcard;
import com.milaboratory.mixcr.trees.DebugInfo;
import com.milaboratory.mixcr.util.Java9Util;
import com.milaboratory.mixcr.util.XSV;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuildSHMTreeReport extends AbstractCommandReport {
    private List<StepResult> stepResults = new ArrayList<>();

    @Override
    public String getCommand() {
        return CommandBuildSHMTree.BUILD_SHM_TREE_COMMAND_NAME;
    }

    @JsonProperty("stepResults")
    public List<StepResult> getStepResults() {
        return stepResults;
    }

    public void onStepEnd(BuildSHMTreeStep step, int clonesWasAdded, int treesCountDelta, CommandBuildSHMTree.Debug debug) {
        List<Map<String, String>> debugInfosBefore = XSV.readXSV(debug.treesBeforeDecisions, DebugInfo.COLUMNS_FOR_XSV.keySet(), ";");
        List<Map<String, String>> debugInfosAfter = XSV.readXSV(debug.treesAfterDecisions, DebugInfo.COLUMNS_FOR_XSV.keySet(), ";");
        List<Integer> commonVJMutationsCounts = debugInfosAfter.stream()
                .filter(row -> Objects.equals(row.get("parentId"), "0"))
                .map(row -> getMutations(row, "VMutationsFromRoot").size() + getMutations(row, "JMutationsFromRoot").size())
                .collect(Collectors.toList());
        Collection<Long> clonesCountInTrees = debugInfosAfter.stream()
                .filter(row -> row.get("cloneId") != null)
                .collect(Collectors.groupingBy(
                        BuildSHMTreeReport::treeId,
                        Collectors.counting()
                ))
                .values();
        Map<String, List<NucleotideSequence>> NDNsByTrees = debugInfosAfter.stream()
                .filter(row -> !row.get("id").equals("0"))
                .collect(Collectors.groupingBy(
                        BuildSHMTreeReport::treeId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                it -> it.stream()
                                        .sorted(Comparator.comparing(row -> Integer.parseInt(row.get("id"))))
                                        .map(row -> getNucleotideSequence(row, "NDN"))
                                        .collect(Collectors.toList())
                        )
                ));
        List<Integer> rootNDNSizes = NDNsByTrees.values().stream()
                .map(it -> it.get(0).size())
                .collect(Collectors.toList());
        double averageNDNWildcardsScore = NDNsByTrees.values().stream()
                .flatMap(Collection::stream)
                .filter(it -> it.size() != 0)
                .collect(Collectors.averagingDouble(BuildSHMTreeReport::wildcardsScore));

        List<Double> NDNsWildcardsScoreForRoots = NDNsByTrees.values().stream()
                .map(it -> it.get(0))
                .filter(it -> it.size() != 0)
                .map(BuildSHMTreeReport::wildcardsScore)
                .collect(Collectors.toList());

        List<Double> maxNDNsWildcardsScoreInTree = NDNsByTrees.values().stream()
                .map(NDNs -> NDNs.stream()
                        .filter(it -> it.size() != 0)
                        .map(BuildSHMTreeReport::wildcardsScore)
                        .max(Comparator.naturalOrder())
                )
                .flatMap(Java9Util::stream)
                .collect(Collectors.toList());

        Collection<Double> surenessOfDecisions = debugInfosBefore.stream()
                .filter(row -> row.get("cloneId") != null)
                .filter(row -> row.get("decisionMetric") != null)
                .collect(Collectors.groupingBy(
                        row -> row.get("cloneId"),
                        Collectors.mapping(
                                row -> Double.valueOf(row.get("decisionMetric")),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        metrics -> {
                                            double minMetric = metrics.stream().mapToDouble(it -> it).min().orElseThrow(IllegalStateException::new);
                                            double maxMetric = metrics.stream().mapToDouble(it -> it).max().orElseThrow(IllegalStateException::new);
                                            return (maxMetric - minMetric) / maxMetric;
                                        }
                                )
                        )
                ))
                .values();

        Map<String, List<Double>> mutationRatesDifferences = debugInfosAfter.stream()
                .filter(row -> row.get("parentId") != null)
                .filter(row -> row.get("NDN") != null)
                .collect(Collectors.groupingBy(
                        BuildSHMTreeReport::treeId,
                        Collectors.mapping(
                                BuildSHMTreeReport::mutationsRateDifference,
                                Collectors.toList()
                        )
                ));

        //TODO diagram and median
        double averageMutationsRateDifference = mutationRatesDifferences.values().stream()
                .flatMap(Collection::stream)
                .mapToDouble(it -> it)
                .average().orElse(0.0);

        List<Double> minMutationsRateDifferences = mutationRatesDifferences.values().stream()
                .map(rateDifferences -> rateDifferences.stream().mapToDouble(it -> it).min().orElse(0.0))
                .collect(Collectors.toList());

        List<Double> maxMutationsRateDifferences = mutationRatesDifferences.values().stream()
                .map(rateDifferences -> rateDifferences.stream().mapToDouble(it -> it).max().orElse(0.0))
                .collect(Collectors.toList());

        stepResults.add(new StepResult(step, clonesWasAdded, treesCountDelta));
    }

    private static double mutationsRateDifference(Map<String, String> row) {
        Mutations<NucleotideSequence> VMutations = getMutations(row, "VMutationsFromParent");
        int VLength = Stream.concat(
                        Arrays.stream(row.get("VRangeWithoutCDR3").split(",")),
                        Stream.of(row.get("VRangeInCDR3"))
                )
                .map(DebugInfo::decodeRange)
                .mapToInt(Range::length)
                .sum();

        Mutations<NucleotideSequence> JMutations = getMutations(row, "JMutationsFromParent");
        int JLength = Stream.concat(
                        Arrays.stream(row.get("JRangeWithoutCDR3").split(",")),
                        Stream.of(row.get("JRangeInCDR3"))
                )
                .map(DebugInfo::decodeRange)
                .mapToInt(Range::length)
                .sum();

        Mutations<NucleotideSequence> NDNMutations = getMutations(row, "NDNMutationsFromParent");
        int NDNLength = getNucleotideSequence(row, "NDN").size();

        double VJMutationsRate = (VMutations.size() + JMutations.size()) / (double) (VLength + JLength);
        double NDNMutationsRate = (NDNMutations.size()) / (double) NDNLength;

        return Math.abs(VJMutationsRate - NDNMutationsRate);
    }

    private static NucleotideSequence getNucleotideSequence(Map<String, String> row, String columnName) {
        if (row.get(columnName) != null) {
            return new NucleotideSequence(row.get(columnName));
        } else {
            return NucleotideSequence.EMPTY;
        }
    }

    private static Mutations<NucleotideSequence> getMutations(Map<String, String> row, String columnName) {
        if (row.get(columnName) != null) {
            return new Mutations<>(NucleotideSequence.ALPHABET, row.get(columnName));
        } else {
            return Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
        }
    }

    private static String treeId(Map<String, String> row) {
        return row.get("VGeneName") + row.get("JGeneName") + row.get("CDR3Length") + row.get("treeId");
    }

    private static double wildcardsScore(NucleotideSequence NDN) {
        int wildcardsScore = 0;
        for (int i = 0; i < NDN.size(); i++) {
            Wildcard wildcard = NucleotideSequence.ALPHABET.codeToWildcard(NDN.codeAt(i));
            wildcardsScore += wildcard.basicSize();
        }
        return wildcardsScore / (double) NDN.size();
    }

    @Override
    public void writeReport(ReportHelper helper) {
        for (int i = 0; i < stepResults.size(); i++) {
            StepResult stepResult = stepResults.get(i);
            helper.writeField("step " + (i + 1), stepResult.step.forPrint);
            if (stepResult.step != BuildSHMTreeStep.CombineTrees) {
                helper.writeField("Clones was added", stepResult.clonesWasAdded);
            }
            if (stepResult.step == BuildSHMTreeStep.BuildingInitialTrees) {
                helper.writeField("Trees created", stepResult.treesCountDelta);
            } else if (stepResult.step == BuildSHMTreeStep.CombineTrees) {
                helper.writeField("Trees combined", -stepResult.treesCountDelta);
            }
        }
    }

    public static class StepResult {
        private final BuildSHMTreeStep step;
        private final int clonesWasAdded;
        private final int treesCountDelta;

        public StepResult(BuildSHMTreeStep step, int clonesWasAdded, int treesCountDelta) {
            this.step = step;
            this.clonesWasAdded = clonesWasAdded;
            this.treesCountDelta = treesCountDelta;
        }

        @JsonProperty("step")
        public BuildSHMTreeStep getStep() {
            return step;
        }

        @JsonProperty("clonesWasAdded")
        public int getClonesWasAdded() {
            return clonesWasAdded;
        }

        @JsonProperty("treesCountDelta")
        public int getTreesCountDelta() {
            return treesCountDelta;
        }
    }
}
