package com.milaboratory.mixcr.cli;

import com.beust.jcommander.JCommander;
import org.junit.Test;

/**
 *
 */
public class UberActionTest {
    @Test
    public void test1() {
        String[] args = {"--align", "\"--species hs\"", "--align", "\"-OvParameters=xxx\"", "--resume", "input", "output", "-wsd"};

        UberAction.UberActionParameters p = new UberAction.UberActionParameters();

        JCommander jCommander = new JCommander(p);
        jCommander.setAcceptUnknownOptions(true);
        jCommander.parse(args);

        System.out.println(p.alignParameters);
    }
}