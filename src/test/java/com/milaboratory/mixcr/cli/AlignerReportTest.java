package com.milaboratory.mixcr.cli;

import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class AlignerReportTest {

    @Test
    public void test1() throws Exception {
        AlignerReport rep = new AlignerReport();
        assertNotNull(GlobalObjectMappers.PRETTY.writeValueAsString(rep));
    }
}