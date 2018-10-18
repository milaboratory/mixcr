package com.milaboratory.mixcr.cli;

import org.junit.Ignore;
import org.junit.Test;
import picocli.AutoComplete;

/**
 *
 */
public class MainTest {

    @Test
    public void test1() {
        Main.main("analyze", "help", "amplicon");
    }

    @Ignore
    @Test
    public void test2_completion() {
        System.out.println(AutoComplete.bash("mixcr", Main.mkCmd()));
    }
}