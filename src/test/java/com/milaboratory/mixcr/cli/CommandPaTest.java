package com.milaboratory.mixcr.cli;

import junit.framework.TestCase;
import org.junit.Test;

/**
 *
 */
public class CommandPaTest extends TestCase {
    @Test
    public void test1() {
        Main.main("postanalysis", "help", "overlap");
    }
}
