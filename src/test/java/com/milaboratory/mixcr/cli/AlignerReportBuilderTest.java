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

import com.milaboratory.mitool.helpers.IOKt;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.*;

public class AlignerReportBuilderTest {

    @Test
    public void testIsSerializable() throws Exception {
        AlignerReport rep = reportBuilder().buildReport();
        assertNotNull(IOKt.getK_PRETTY_OM().writeValueAsString(rep));
    }

    @Test
    public void testNotSerializeDate() throws Exception {
        AlignerReport rep = reportBuilder().buildReport();
        String asJson = IOKt.getK_PRETTY_OM().writeValueAsString(rep);
        assertNull(IOKt.getK_PRETTY_OM().readValue(asJson, AlignerReport.class).getDate());
    }

    @Test
    public void testSerializeInputFiles() throws Exception {
        AlignerReport rep = reportBuilder()
                .setInputFiles("file1")
                .buildReport();
        String asJson = IOKt.getK_PRETTY_OM().writeValueAsString(rep);
        assertArrayEquals(new String[]{"file1"}, IOKt.getK_PRETTY_OM().readValue(asJson, AlignerReport.class).getInputFiles());
    }

    @Test
    public void testSerializeCommandLine() throws Exception {
        AlignerReport rep = reportBuilder()
                .setCommandLine("cmd args")
                .buildReport();
        String asJson = IOKt.getK_PRETTY_OM().writeValueAsString(rep);
        assertEquals("cmd args", IOKt.getK_PRETTY_OM().readValue(asJson, AlignerReport.class).getCommandLine());
    }
    
    private AlignerReportBuilder reportBuilder() {
        return new AlignerReportBuilder()
                .setCommandLine("from test")
                .setStartMillis(123)
                .setFinishMillis(123)
                .setInputFiles()
                .setOutputFiles();
    }
}
