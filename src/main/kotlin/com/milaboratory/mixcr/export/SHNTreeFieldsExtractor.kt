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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.export

import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.helpers.get
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.VJ_REFERENCE

object SHNTreeFieldsExtractor : BaseFieldExtractors() {
    override fun initFields(): Array<Field<out Any>> {
        val fields = mutableListOf<Field<SHMTreeForPostanalysis>>()

        fields += FieldParameterless(
            "-treeId",
            "SHM tree id",
            "Tree id",
            "treeId"
        ) { it.meta.treeId.toString() }

        fields += FieldParameterless(
            "-uniqClonesCount",
            "Number of uniq clones in the SHM tree",
            "Uniq clones count",
            "uniqClonesCount"
        ) { shmTree ->
            shmTree.tree.allNodes().sumOf { it.node.content.clones.count() }.toString()
        }

        fields += FieldParameterless(
            "-totalClonesCount",
            "Total sum of counts of clones in the SHM tree",
            "Total clones count",
            "totalClonesCount"
        ) { shmTree ->
            shmTree.tree.allNodes().sumOf { (_, node) -> node.content.clones.sumOf { it.clone.count } }.toString()
        }

        fields += FieldParameterless(
            "-wildcardsScore",
            "Count of possible nucleotide sequences of CDR3 in MRCA",
            "Wildcards score",
            "wildcardsScore"
        ) { shmTree ->
            val CDR3Sequence = shmTree.mrca.targetNSequence(CDR3)!!
            val wildcardSized = (0 until CDR3Sequence.size())
                .map { CDR3Sequence[it] }
                .filter { NucleotideSequence.ALPHABET.isWildcard(it) }
                .map { NucleotideSequence.ALPHABET.codeToWildcard(it) }
                .map { it.basicSize() }
            wildcardSized.fold(1, Int::times).toString()
        }

        fields += FieldParameterless(
            "-ndnOfMRCA",
            "NDN nucleotide sequence of MRCA",
            "mrcaNDN",
            "mrcaNDN"
        ) { shmTree ->
            shmTree.mrca.NDN.toString()
        }

        // Best hits
        for (type in VJ_REFERENCE) {
            val l = type.letter
            fields.add(
                FieldParameterless(
                    "-${l.lowercaseChar()}Hit",
                    "Export best $l hit",
                    "Best $l hit",
                    "best${l}Hit"
                ) {
                    it.meta.rootInfo.VJBase.geneIds[type].name
                }
            )
        }

        return fields.toTypedArray()
    }
}