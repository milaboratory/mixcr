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

import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.ClnsReader;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.ReportUtil;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static com.milaboratory.mixcr.cli.CommandExportReports.EXPORT_REPORTS_COMMAND_NAME;


@Command(name = EXPORT_REPORTS_COMMAND_NAME,
        separator = " ",
        description = "Export MiXCR reports.")
public class CommandExportReports extends MiXCRCommand {
    public static final String EXPORT_REPORTS_COMMAND_NAME = "exportReports";

    @Parameters(description = "data.[vdjca|clns|clna]", index = "0")
    public String in;

    @Parameters(description = "report.[txt|jsonl]", index = "1", arity = "0..1")
    public String out = null;

    @Option(names = {"--json"},
            description = "Export as json lines")
    public boolean json = false;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return out == null ? Collections.emptyList() : Collections.singletonList(out);
    }

    @Override
    public void run0() throws Exception {
        ReportHelper helper = json
                ? null
                : out == null ? new ReportHelper(System.out, true) : new ReportHelper(out);

        List<MiXCRCommandReport> reports;
        switch (IOUtil.extractFileType(Paths.get(in))) {
            case VDJCA:
                try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in, VDJCLibraryRegistry.getDefault())) {
                    reports = reader.reports();
                }
                break;
            case CLNS:
                try (ClnsReader reader = new ClnsReader(in, VDJCLibraryRegistry.getDefault())) {
                    reports = reader.reports();
                }
                break;
            case CLNA:
                try (ClnAReader reader = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), 1)) {
                    reports = reader.reports();
                }
                break;
            default:
                throw new RuntimeException();
        }

        if (json) {
            if (out != null)
                for (MiXCRCommandReport report : reports)
                    ReportUtil.appendJsonReport(out, report);
            else
                System.out.println(GlobalObjectMappers.getPretty().writeValueAsString(reports));
        } else
            for (MiXCRCommandReport report : reports) {
                if (helper != null)
                    report.writeReport(helper);
            }
    }
}
