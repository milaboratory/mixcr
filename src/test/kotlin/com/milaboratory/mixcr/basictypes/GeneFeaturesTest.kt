package com.milaboratory.mixcr.basictypes

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.util.GlobalObjectMappers
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.JCDR3Part
import io.repseq.core.GeneFeature.VCDR3Part
import org.junit.Test

class GeneFeaturesTest {
    @Test
    fun `serialize with one gene feature`() {
        val geneFeatures = GeneFeatures(CDR3)
        K_OM.writeValueAsString(geneFeatures) shouldBe """["CDR3"]"""
        K_OM.readValue<GeneFeatures>("""["CDR3"]""") shouldBe geneFeatures
    }

    @Test
    fun `serialize with several gene features`() {
        val geneFeatures = GeneFeatures(listOf(VCDR3Part, JCDR3Part))
        K_OM.writeValueAsString(geneFeatures) shouldBe """["VCDR3Part","JCDR3Part"]"""
        K_OM.readValue<GeneFeatures>("""["VCDR3Part","JCDR3Part"]""") shouldBe geneFeatures
    }
}
