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
package com.milaboratory.mixcr.tags;

import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.ProgressAndStage;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Paths;

/**
 *
 */
// public class DropletCloneGraphTest {
//     @Test
//     @Ignore
//     public void test1() throws Exception {
//         try (ClnAReader reader = new ClnAReader(Paths.get("./tmp/b_analysis.clna"), VDJCLibraryRegistry.getDefault(), 3)) {
//             CloneSet clns = reader.readCloneSet();
//             System.out.println("# clones " + clns.size());
//             DropletCloneGraph.CloneTagTupleList links = DropletCloneGraph.calculateTuples(clns, 0);
//             DropletCloneGraphReport report = new DropletCloneGraphReport();
//             ProgressAndStage progressAndStage = new ProgressAndStage("Grouping");
//             SmartProgressReporter.startProgressReport(progressAndStage);
//             DropletCloneGraph.calculateGroups(links, DropletCloneGraphParameters.getDefault(), report, progressAndStage);
//             report.writeReport(ReportHelper.STDOUT);
//         }
//     }
//
//     @Test
//     @Ignore
//     public void test2() throws Exception {
//         try (ClnAReader reader = new ClnAReader(Paths.get("./tmp/t_analysis.clna"), VDJCLibraryRegistry.getDefault(), 3)) {
//             CloneSet clns = reader.readCloneSet();
//             System.out.println("# clones " + clns.size());
//             DropletCloneGraph.CloneTagTupleList links = DropletCloneGraph.calculateTuples(clns, 0);
//             DropletCloneGraphReport report = new DropletCloneGraphReport();
//             ProgressAndStage progressAndStage = new ProgressAndStage("Grouping");
//             SmartProgressReporter.startProgressReport(progressAndStage);
//             DropletCloneGraphParameters parameters = DropletCloneGraphParameters.getDefault();
//             DropletCloneGraph.calculateGroups(links, parameters, report, progressAndStage);
//             report.writeReport(ReportHelper.STDOUT);
//         }
//     }
// }
