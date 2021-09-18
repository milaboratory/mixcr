/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import org.junit.Ignore;
import org.junit.Test;
import picocli.AutoComplete;

/**
 *
 */
public class MainTest {

    @Ignore
    @Test
    public void test1() {
        Main.main("analyze", "help", "amplicon");
    }

    @Ignore
    @Test
    public void test2() {
        Main.main("align", "help");
    }

    @Ignore
    @Test
    public void test2_completion() {
        System.out.println(AutoComplete.bash("mixcr", Main.mkCmd()));
    }
}
