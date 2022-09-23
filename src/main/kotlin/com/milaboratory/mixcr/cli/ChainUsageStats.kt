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
package com.milaboratory.mixcr.cli

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.milaboratory.util.ReportHelper
import io.repseq.core.Chains

@JsonAutoDetect
data class ChainUsageStats(
    @JsonProperty("chimeras") val chimeras: Long,
    @JsonProperty("total") val total: Long,
    @JsonProperty("chains")
    @JsonSerialize(keyUsing = Chains.ChainsKeySerializer::class)
    @JsonDeserialize(keyUsing = Chains.ChainsKeyDeserializer::class)
    val chains: Map<Chains, ChainUsageStatsRecord>
) : MiXCRReport {
    override fun writeReport(helper: ReportHelper) {
        val total = total
        for ((key, value) in chains) {
            helper.writePercentAndAbsoluteField("$key chains", value.total, total)
            helper.writePercentAndAbsoluteField(
                "$key non-functional",
                value.nonFunctional, value.total
            )
        }
    }
}