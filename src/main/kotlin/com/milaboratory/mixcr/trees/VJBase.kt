package com.milaboratory.mixcr.trees

import io.repseq.core.GeneType

data class VJBase(val VGeneName: String, val JGeneName: String, val CDR3length: Int?) {
    fun getGeneName(geneType: GeneType): String = when (geneType) {
        GeneType.Variable -> VGeneName
        GeneType.Joining -> JGeneName
        else -> throw IllegalArgumentException()
    }
}
