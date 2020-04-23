package com.milaboratory.mixcr.cli;

import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
public class CommandGroupCellsTest {
    @Test
    @Ignore
    public void test1() {
        Main.main("groupCells", "-f", "--tag", "0", "./tmp/t_analysis.clna", "./tmp/t_analysis_grouped_OU.clna");
    }
}
