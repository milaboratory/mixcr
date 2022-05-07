@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.sequence.NucleotideSequence

internal class AncestorInfoBuilder {
    fun buildAncestorInfo(ancestor: MutationsDescription): AncestorInfo {
        val builder = NucleotideSequence.ALPHABET.createBuilder()
        ancestor.VMutationsWithoutCDR3.stream()
            .map { obj: MutationsWithRange -> obj.buildSequence() }
            .forEach { seq: NucleotideSequence -> builder.append(seq) }
        val CDR3Begin = builder.size()
        builder.append(ancestor.VMutationsInCDR3WithoutNDN.buildSequence())
        builder.append(ancestor.knownNDN.buildSequence())
        builder.append(ancestor.JMutationsInCDR3WithoutNDN.buildSequence())
        val CDR3End = builder.size()
        ancestor.JMutationsWithoutCDR3.stream()
            .map { obj: MutationsWithRange -> obj.buildSequence() }
            .forEach { seq: NucleotideSequence -> builder.append(seq) }
        return AncestorInfo(
            builder.createAndDestroy(),
            CDR3Begin,
            CDR3End
        )
    }
}
