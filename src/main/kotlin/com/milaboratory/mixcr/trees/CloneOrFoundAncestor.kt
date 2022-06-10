package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.TargetPartitioning
import com.milaboratory.mixcr.basictypes.VDJCHit
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.PartitionedSequence
import java.util.*

class CloneOrFoundAncestor(
    private val id: Int,
    private val cloneInfo: CloneInfo?,
    private val targets: Array<NucleotideSequence>,
    private val hits: EnumMap<GeneType, VDJCHit>
) {

    private val partitionedTargets: Array<PartitionedSequence<NucleotideSequence>> by lazy {
        Array(targets.size) { i ->
            val partitioning = TargetPartitioning(i, hits)
            object : PartitionedSequence<NucleotideSequence>() {
                override fun getSequence(range: Range) = targets[i].getRange(range)

                override fun getPartitioning() = partitioning
            }
        }
    }

    fun getFeature(geneFeature: GeneFeature): NucleotideSequence? =
        partitionedTargets.firstNotNullOfOrNull { it.getFeature(geneFeature) }

    class CloneInfo(
        private val id: Int,
        private val count: Double,
        private val CGeneName: String?,
        private val datasetId: Int
    )

}
