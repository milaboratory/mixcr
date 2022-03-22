package com.milaboratory.mixcr.trees;

import com.google.common.collect.ImmutableMap;
import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DebugInfo {
    public static Map<String, Function<DebugInfo, Object>> COLUMNS_FOR_XSV = ImmutableMap.<String, Function<DebugInfo, Object>>builder()
            .put("VGeneName", it -> it.rootInfo.getVJBase().VGeneName)
            .put("JGeneName", it -> it.rootInfo.getVJBase().JGeneName)
            .put("CDR3Length", it -> it.rootInfo.getVJBase().CDR3length)
            .put("VRangeWithoutCDR3", it -> it.VRangesWithoutCDR3.stream().map(DebugInfo::encodeRange).collect(Collectors.joining(",")))
            .put("VRangeInCDR3", it -> encodeRange(it.rootInfo.getVRangeInCDR3()))
            .put("JRangeInCDR3", it -> encodeRange(it.rootInfo.getJRangeInCDR3()))
            .put("JRangeWithoutCDR3", it -> it.JRangesWithoutCDR3.stream().map(DebugInfo::encodeRange).collect(Collectors.joining(",")))
            .put("treeId", it -> it.treeId.getId())
            .put("cloneId", it -> it.cloneId)
            .put("id", it -> it.id)
            .put("parentId", it -> it.parentId)
            .put("NDN", it -> it.NDN)
            .put("VMutationsFromRoot", it -> it.mutationsFromRoot.VMutations.encode())
            .put("JMutationsFromRoot", it -> it.mutationsFromRoot.JMutations.encode())
            .put("NDNMutationsFromRoot", it -> it.mutationsFromRoot.NDNMutations.encode())
            .put("VMutationsFromParent", it -> it.mutationsFromParent != null ? it.mutationsFromParent.VMutations.encode() : null)
            .put("JMutationsFromParent", it -> it.mutationsFromParent != null ? it.mutationsFromParent.JMutations.encode() : null)
            .put("NDNMutationsFromParent", it -> it.mutationsFromParent != null ? it.mutationsFromParent.NDNMutations.encode() : null)
            .put("decisionMetric", it -> it.decisionMetric)
            .put("publicClone", it -> it.publicClone)
            .build();

    private static String encodeRange(Range range) {
        return range.getLower() + "-" + range.getUpper();
    }

    public static Range decodeRange(String raw) {
        String[] split = raw.split("-");
        return new Range(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }

    private final TreeWithMetaBuilder.TreeId treeId;
    private final RootInfo rootInfo;
    private final List<Range> VRangesWithoutCDR3;
    private final List<Range> JRangesWithoutCDR3;
    private final Integer cloneId;
    private final int id;
    private final Integer parentId;
    private final NucleotideSequence NDN;
    private final MutationsSet mutationsFromRoot;
    private final MutationsSet mutationsFromParent;
    private final Double decisionMetric;
    private final boolean publicClone;


    public DebugInfo(
            TreeWithMetaBuilder.TreeId treeId,
            RootInfo rootInfo,
            List<Range> VRangesWithoutCDR3,
            List<Range> JRangesWithoutCDR3,
            Integer cloneId,
            int id,
            Integer parentId,
            NucleotideSequence NDN,
            MutationsSet mutationsFromRoot,
            MutationsSet mutationsFromParent,
            Double decisionMetric,
            boolean publicClone
    ) {
        this.treeId = treeId;
        this.rootInfo = rootInfo;
        this.VRangesWithoutCDR3 = VRangesWithoutCDR3;
        this.JRangesWithoutCDR3 = JRangesWithoutCDR3;
        this.cloneId = cloneId;
        this.id = id;
        this.parentId = parentId;
        this.NDN = NDN;
        this.mutationsFromRoot = mutationsFromRoot;
        this.mutationsFromParent = mutationsFromParent;
        this.decisionMetric = decisionMetric;
        this.publicClone = publicClone;
    }

    public static class MutationsSet {
        private final Mutations<NucleotideSequence> VMutations;
        private final Mutations<NucleotideSequence> NDNMutations;
        private final Mutations<NucleotideSequence> JMutations;

        public MutationsSet(
                Mutations<NucleotideSequence> VMutations,
                Mutations<NucleotideSequence> NDNMutations,
                Mutations<NucleotideSequence> JMutations
        ) {
            this.VMutations = VMutations;
            this.NDNMutations = NDNMutations;
            this.JMutations = JMutations;
        }
    }
}
