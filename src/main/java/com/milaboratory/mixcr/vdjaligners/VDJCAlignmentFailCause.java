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
package com.milaboratory.mixcr.vdjaligners;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum VDJCAlignmentFailCause {
    @JsonEnumDefaultValue
    NoHits("Alignment failed, no hits (not TCR/IG?)", "No hits (not TCR/IG?)"),
    NoCDR3Parts("Alignment failed because of absence of CDR3 parts", "No CDR3 parts"),
    NoVHits("Alignment failed because of absence of V hits", "No V hits"),
    NoJHits("Alignment failed because of absence of J hits", "No J hits"),
    VAndJOnDifferentTargets("No target with both V and J alignments", "No target with both V and J"),
    LowTotalScore("Alignment failed because of low total score", "Low total score"),
    NoBarcode("Absent barcode", "Absent barcode");
    public final String reportLine;
    public final String shortReportLine;

    VDJCAlignmentFailCause(String reportLine, String shortReportLine) {
        this.reportLine = reportLine;
        this.shortReportLine = shortReportLine;
    }
}
