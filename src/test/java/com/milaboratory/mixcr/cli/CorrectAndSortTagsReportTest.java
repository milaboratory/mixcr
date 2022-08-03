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

import com.milaboratory.mitool.refinement.CorrectionReport;
import com.milaboratory.mitool.refinement.CorrectionStepReport;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.test.TestUtil;
import com.milaboratory.util.GlobalObjectMappers;
import kotlin.Unit;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;

import static com.fasterxml.jackson.module.kotlin.ExtensionsKt.kotlinModule;

public class CorrectAndSortTagsReportTest {
    @Before
    public void before() {
        GlobalObjectMappers.addModifier(om -> om.registerModule(kotlinModule(builder -> Unit.INSTANCE)));
    }

    @Test
    public void testNullReport() {
        CorrectAndSortTagsReport r = new CorrectAndSortTagsReport(
                new Date(),
                "",
                new String[0],
                new String[0],
                0L,
                MiXCRVersionInfo.get().getShortestVersionString(),
                null
        );
        TestUtil.assertJson(r);
    }

    @Test
    public void testNotNullReport() {
        CorrectAndSortTagsReport r = new CorrectAndSortTagsReport(
                new Date(),
                "",
                new String[0],
                new String[0],
                0L,
                MiXCRVersionInfo.get().getShortestVersionString(),
                new CorrectionReport(Collections.singletonList(new CorrectionStepReport("step1")))
        );
        TestUtil.assertJson(r);
    }
}