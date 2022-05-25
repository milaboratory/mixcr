package com.milaboratory.mixcr.alleles

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

class FindAllelesByNativeCellsTest {
    @Test
    fun `all clones are the same`() {
        val result = search(
            clone("ST1G,ST3G", 10),
            clone("ST1G,ST3G", 11),
            clone("ST1G,ST3G", 12),
            clone("ST1G,ST3G", 13),
            clone("ST1G,ST3G", 14),
            clone("ST1G,ST3G", 15)
        )
        result shouldContainExactlyInAnyOrder listOf(
            allele("ST1G,ST3G")
        )
    }

    @Test
    fun `all clones without mutations`() {
        val result = search(
            clone("", 10),
            clone("", 11),
            clone("", 12),
            clone("", 13),
            clone("", 14),
            clone("", 15)
        )
        result shouldContainExactlyInAnyOrder listOf(
            allele("")
        )
    }

    @Test
    fun `there are several mutated variance of an allele`() {
        val result = search(
            clone("ST1G,ST2G", 9),
            clone("ST1G", 10),
            clone("ST1G,ST3G,ST4G", 10),
            clone("ST1G,ST3G,ST4G,ST5G", 10),
            clone("ST1G", 11),
            clone("ST1G", 12),
            clone("ST1G", 13),
            clone("ST1G", 14),
            clone("ST1G", 15)
        )
        result shouldContainExactlyInAnyOrder listOf(
            allele("ST1G")
        )
    }

    @Test
    fun `there are not enough native cells to make decision`() {
        val result = search(
            clone("ST1G,ST2G", 9),
            clone("ST1G", 10),
            clone("ST1G,ST3G,ST4G", 10),
            clone("ST1G,ST3G,ST4G,ST5G", 10),
            clone("ST1G", 11),
            clone("ST1G", 12),
        )
        result shouldBe emptyList()
    }

    private fun allele(mutations: String) = AllelesSearcher.Result(
        Mutations(NucleotideSequence.ALPHABET, mutations)
    )

    private fun search(vararg clones: CloneDescription) =
        ByNativeCellsSearcher(minDiversity = 5).search(clones.toList())

    private fun clone(mutations: String, CDR3Length: Int) = CloneDescription(
        Mutations(NucleotideSequence.ALPHABET, mutations),
        CDR3Length * 3,
        "J*00"
    )
}
