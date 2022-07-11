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
package com.milaboratory.mixcr.cli;

import com.milaboratory.util.ReportHelper;

public class TagReport implements MiXCRReport {
//    @JsonProperty("totalReads")
//    public final long totalReads;
//    @JsonProperty("matched")
//    public final long matched;
//
//    @JsonCreator
//    public TagReport(@JsonProperty("totalReads") long totalReads,
//                     @JsonProperty("matched") long matched) {
//        this.totalReads = totalReads;
//        this.matched = matched;
//    }

    @Override
    public void writeReport(ReportHelper helper) {
//        helper.writeField("Total reads", totalReads);
//        helper.writePercentAndAbsoluteField("Matched reads", matched, totalReads);
    }
}
