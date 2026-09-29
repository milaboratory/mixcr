package com.milaboratory.mixcr

import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneVariantName
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import org.junit.Test

/**
 * Species and chains added in the built-in reference library (v6.4, v6.5) must resolve from the packaged default library.
 */
class DefaultLibraryTest {
    private class Species(val name: String, val taxonId: Long, val chains: List<String>, val representativeV: String)

    private val species = listOf(
        Species("dog", 9615, listOf("IGH", "IGK", "IGL", "TRA", "TRB", "TRD", "TRG"), "TRBV1*00"),
        Species("pig", 9823, listOf("IGH", "IGK", "IGL", "TRA", "TRB", "TRD", "TRG"), "TRBV1*00"),
        Species("salmon", 8030, listOf("IGH", "IGK", "IGL", "TRA", "TRB", "TRD", "TRG"), "TRGV1-1*00"),
        Species("gallus", 9031, listOf("IGH", "IGL"), "IGLV1-1*00"),
        Species("mmul", 9544, listOf("IGH", "IGK", "IGL", "TRA", "TRB"), "IGKV1-6*00"),
        Species("mfas", 9541, listOf("IGH", "IGK", "IGL", "TRA", "TRB"), "IGKV1-6*00"),
        Species("rat", 10116, listOf("IGH", "IGK", "IGL", "TRA", "TRB"), "IGHV1-8*00"),
    )

    @Test
    fun `new species resolve with functional V and J for every chain`() {
        assertSoftly {
            species.forEach { sp ->
                val library = VDJCLibraryRegistry.getDefault().getLibrary("default", sp.name)
                sp.name.asClue {
                    library.taxonId shouldBe sp.taxonId
                    sp.chains.forEach { chain ->
                        "${sp.name} $chain".asClue {
                            val genes = library.getPrimaryGenes(Chains.parse(chain)).filter { it.isFunctional }
                            genes.count {
                                it.geneType == GeneType.Variable && it.referencePoints.getPosition(ReferencePoint.CDR3Begin) >= 0
                            } shouldNotBe 0
                            genes.count {
                                it.geneType == GeneType.Joining && it.referencePoints.getPosition(ReferencePoint.CDR3End) >= 0
                            } shouldNotBe 0
                        }
                    }
                    val v = library[GeneVariantName(sp.representativeV)]
                    v.isFunctional shouldBe true
                    v.getFeature(GeneFeature.VRegion) shouldNotBe null
                }
            }
        }
    }

    @Test
    fun `references corrected in v6_5 resolve from the built-in library`() {
        fun gene(species: String, name: String) =
            VDJCLibraryRegistry.getDefault().getLibrary("default", species)[GeneVariantName(name)]
        assertSoftly {
            // rhesus IGHV leaders sit next to the V exon (they were 3-45 kb away before v6.5)
            listOf("IGHV3-182*00", "IGHV4-86*00", "IGHV5-189*00").forEach { name ->
                name.asClue { gene("mmul", name).getFeature(GeneFeature.VIntron)!!.size() shouldBeInRange 50..400 }
            }
            // rhesus TRBC2 has its first exon
            "TRBC2*00".asClue { gene("mmul", "TRBC2*00").getFeature(GeneFeature.CExon1)!!.size() shouldBe 387 }
            // cynomolgus TRAV24 and rat TRAV3-4 have a complete V transcript
            listOf("mfas" to "TRAV24*00", "rat" to "TRAV3-4*00").forEach { (species, name) ->
                "$species $name".asClue {
                    val v = gene(species, name)
                    v.isFunctional shouldBe true
                    v.getFeature(GeneFeature.VTranscript)!!.containsWildcards() shouldBe false
                }
            }
        }
    }
}
