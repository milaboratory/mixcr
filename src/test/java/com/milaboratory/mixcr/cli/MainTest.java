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
    public void test3() {
        Main.main("exportClones",
                "-nMutations",
                "{FR1Begin:FR3End}",
                "-count",
                "-nMutations",
                "FR4",
                "/Users/dbolotin/tst");
    }

    @Ignore
    @Test
    public void test2_completion() {
        System.out.println(AutoComplete.bash("mixcr", Main.mkCmd()));
    }
}
