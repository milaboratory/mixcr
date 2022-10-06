package com.milaboratory.mixcr.basictypes

import com.fasterxml.jackson.module.kotlin.readValue
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
        GlobalObjectMappers.getOneLine().writeValueAsString(geneFeatures) shouldBe """["CDR3"]"""
        GlobalObjectMappers.getOneLine().readValue<GeneFeatures>(""""CDR3"""") shouldBe geneFeatures
        GlobalObjectMappers.getOneLine().readValue<GeneFeatures>("""["CDR3"]""") shouldBe geneFeatures
    }

    @Test
    fun `serialize with several gene features`() {
        val geneFeatures = GeneFeatures(listOf(VCDR3Part, JCDR3Part))
        GlobalObjectMappers.getOneLine().writeValueAsString(geneFeatures) shouldBe """["VCDR3Part","JCDR3Part"]"""
        GlobalObjectMappers.getOneLine().readValue<GeneFeatures>("""["VCDR3Part","JCDR3Part"]""") shouldBe geneFeatures
    }
}
