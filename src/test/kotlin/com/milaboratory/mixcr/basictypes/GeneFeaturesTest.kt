package com.milaboratory.mixcr.basictypes

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.io.K_OM
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.JCDR3Part
import io.repseq.core.GeneFeature.VCDR3Part
import io.repseq.core.GeneFeature.VDJRegion
import io.repseq.core.GeneFeatures
import org.junit.Test

class GeneFeaturesTest {
    @Test
    fun `serialize with one gene feature`() {
        val geneFeatures = GeneFeatures.fromOrdinal(CDR3)
        K_OM.writeValueAsString(geneFeatures) shouldBe """["CDR3"]"""
        K_OM.readValue<GeneFeatures>("""["CDR3"]""") shouldBe geneFeatures
    }

    @Test
    fun `serialize with several gene features`() {
        val geneFeatures = GeneFeatures(listOf(VCDR3Part, JCDR3Part))
        K_OM.writeValueAsString(geneFeatures) shouldBe """["VCDR3Part","JCDR3Part"]"""
        K_OM.readValue<GeneFeatures>("""["VCDR3Part","JCDR3Part"]""") shouldBe geneFeatures
    }

    @Test
    fun `json override if original was set`() {
        val original = Container("was set", GeneFeatures.fromOrdinal(CDR3))
        val asArray =
            JsonOverrider.override(K_OM, original, Container::class.java, mapOf("features" to """[VDJRegion]"""))
        asArray.features shouldBe GeneFeatures.fromOrdinal(VDJRegion)
        val asArrayWithMany =
            JsonOverrider.override(
                K_OM,
                original,
                Container::class.java,
                mapOf("features" to """[VCDR3Part,JCDR3Part]""")
            )
        asArrayWithMany.features shouldBe GeneFeatures(listOf(VCDR3Part, JCDR3Part))

        val asString = JsonOverrider.override(K_OM, original, Container::class.java, mapOf("features" to "VDJRegion"))
        asString.features shouldBe GeneFeatures.fromOrdinal(VDJRegion)
    }

    @Test
    fun `json override if original was null`() {
        val original = Container("was null", null)
        val asArray =
            JsonOverrider.override(K_OM, original, Container::class.java, mapOf("features" to """[VDJRegion]"""))
        asArray.features shouldBe GeneFeatures.fromOrdinal(VDJRegion)
        val asArrayWithMany =
            JsonOverrider.override(
                K_OM,
                original,
                Container::class.java,
                mapOf("features" to """[VCDR3Part,JCDR3Part]""")
            )
        asArrayWithMany.features shouldBe GeneFeatures(listOf(VCDR3Part, JCDR3Part))

        val asString = JsonOverrider.override(K_OM, original, Container::class.java, mapOf("features" to "VDJRegion"))
        asString.features shouldBe GeneFeatures.fromOrdinal(VDJRegion)
    }

    data class Container(
        val name: String,
        val features: GeneFeatures?
    )
}
