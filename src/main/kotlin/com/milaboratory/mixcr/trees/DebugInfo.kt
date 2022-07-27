/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.trees

import com.google.common.collect.ImmutableMap
import com.milaboratory.core.Range
import com.milaboratory.core.sequence.NucleotideSequence
import java.util.stream.Collectors

class DebugInfo(
    private val treeId: TreeId,
    private val rootInfo: RootInfo,
    private val VRangesWithoutCDR3: Collection<Range>,
    private val JRangesWithoutCDR3: Collection<Range>,
    private val cloneId: CloneWrapper.ID?,
    private val id: Int,
    private val parentId: Int?,
    private val NDN: NucleotideSequence,
    private val mutationsFromRoot: MutationsSet,
    private val mutationsFromParent: MutationsSet?,
    private val decisionMetric: Double?,
    private val publicClone: Boolean
) {
    companion object {
        var COLUMNS_FOR_XSV: Map<String, (DebugInfo) -> Any?> =
            ImmutableMap.builder<String, (DebugInfo) -> Any?>()
                .put("VGeneName") { it.rootInfo.VJBase.geneIds.V.name }
                .put("JGeneName") { it.rootInfo.VJBase.geneIds.J.name }
                .put("CDR3Length") { it.rootInfo.VJBase.CDR3length }
                .put("VRangeWithoutCDR3") {
                    it.VRangesWithoutCDR3.stream().map { range -> encodeRange(range) }
                        .collect(Collectors.joining(","))
                }
                .put("VRangeInCDR3") { encodeRange(it.rootInfo.rangeInCDR3.V) }
                .put("JRangeInCDR3") { encodeRange(it.rootInfo.rangeInCDR3.J) }
                .put("JRangeWithoutCDR3") {
                    it.JRangesWithoutCDR3.stream().map { range -> encodeRange(range) }
                        .collect(Collectors.joining(","))
                }
                .put("treeId") { it.treeId.encode() }
                .put("datasetId") { it.cloneId?.datasetId }
                .put("cloneId") { it.cloneId?.cloneId }
                .put("id") { it.id }
                .put("parentId") { it.parentId }
                .put("NDN") { it.NDN }
                .put("VMutationsFromRoot") { it.mutationsFromRoot.mutations.V.combinedMutations().encode() }
                .put("JMutationsFromRoot") { it.mutationsFromRoot.mutations.J.combinedMutations().encode() }
                .put("NDNMutationsFromRoot") { it.mutationsFromRoot.NDNMutations.mutations.encode() }
                .put("VMutationsFromParent") { it.mutationsFromParent?.mutations?.V?.combinedMutations()?.encode() }
                .put("JMutationsFromParent") { it.mutationsFromParent?.mutations?.J?.combinedMutations()?.encode() }
                .put("NDNMutationsFromParent") { it.mutationsFromParent?.NDNMutations?.mutations?.encode() }
                .put("decisionMetric") { it.decisionMetric }
                .put("publicClone") { it.publicClone }
                .build()

        private fun encodeRange(range: Range): String = range.lower.toString() + "-" + range.upper

        fun decodeRange(raw: String): Range {
            val split = raw.split(Regex("-")).dropLastWhile { it.isEmpty() }.toTypedArray()
            return Range(split[0].toInt(), split[1].toInt())
        }
    }
}
