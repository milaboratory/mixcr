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
package com.milaboratory.mixcr.assembler

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.LinearGapAlignmentScoring
import com.milaboratory.core.sequence.quality.QualityAggregationType
import com.milaboratory.core.tree.TreeSearchParameters
import com.milaboratory.util.K_PRETTY_OM
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.repseq.core.GeneFeature
import org.junit.Test

class CloneAssemblerParametersTest {
    @Test
    fun test1() {
        val factoryParameters = CloneFactoryParameters(
            VJCClonalAlignerParameters(
                0.3f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3
            ),
            VJCClonalAlignerParameters(
                0.4f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5
            ),
            null,
            DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        )
        val params = CloneAssemblerParameters(
            arrayOf(GeneFeature.FR1, GeneFeature.CDR3), 12,
            QualityAggregationType.Average,
            CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, AdvancedClusteringFilter(1E-3, 1E-3, 1E-4)),
            factoryParameters, true, true, false,
            0.4, 2.0, 2.0, true, 20.toByte(), .8, "2", 15.toByte(), null
        )
        val str = K_PRETTY_OM.writeValueAsString(params)
        // System.out.println(str);
        val deser: CloneAssemblerParameters = K_PRETTY_OM.readValue(str)
        deser shouldBe params
        var clone = deser.clone()
        clone shouldBe params
        deser.getCloneFactoryParameters().vParameters.setRelativeMinScore(0.34f)
        clone shouldNotBe deser
        clone = params.clone()
        clone.setMappingThreshold("2of2")
        clone shouldBe params
    }

    @Test
    fun test2() {
        val factoryParameters = CloneFactoryParameters(
            VJCClonalAlignerParameters(
                0.3f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3
            ),
            VJCClonalAlignerParameters(
                0.4f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5
            ),
            null, DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        )
        val params = CloneAssemblerParameters(
            arrayOf(GeneFeature.FR1, GeneFeature.CDR3), 12,
            QualityAggregationType.Average,
            CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, AdvancedClusteringFilter(1E-3, 1E-3, 1E-4)),
            factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, 20.toByte(), .8, "2of6", 15.toByte(), null
        )
        val str = K_PRETTY_OM.writeValueAsString(params)
        // System.out.println(str);
        val deser: CloneAssemblerParameters = K_PRETTY_OM.readValue(str)
        deser shouldBe params
        val clone = deser.clone()
        clone shouldBe params
        deser.getCloneFactoryParameters().vParameters.setRelativeMinScore(0.34f)
        deser shouldNotBe clone
    }
}
