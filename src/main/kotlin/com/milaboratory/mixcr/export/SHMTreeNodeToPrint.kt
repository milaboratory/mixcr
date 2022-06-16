package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.trees.CloneOrFoundAncestor
import com.milaboratory.mixcr.trees.RootInfo
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId

class SHMTreeNodeToPrint(
    val cloneOrFoundAncestor: CloneOrFoundAncestor,
    val rootInfo: RootInfo,
    val assemblerParameters: CloneAssemblerParameters,
    val alignerParameters: VDJCAlignerParameters,
    val geneSupplier: (VDJCGeneId) -> VDJCGene
)
