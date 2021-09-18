/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

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
