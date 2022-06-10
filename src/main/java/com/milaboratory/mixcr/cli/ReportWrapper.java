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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.milaboratory.util.Report;
import com.milaboratory.util.ReportHelper;

public final class ReportWrapper extends AbstractCommandReport {
    private final String action;
    private final Report innerReport;

    public ReportWrapper(String action, Report innerReport) {
        this.action = action;
        this.innerReport = innerReport;
    }

    @Override
    public String getCommand() {
        return action;
    }

    @JsonUnwrapped
    public Report getInnerReport() {
        return innerReport;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        writeSuperReport(helper);
        innerReport.writeReport(helper);
    }
}
