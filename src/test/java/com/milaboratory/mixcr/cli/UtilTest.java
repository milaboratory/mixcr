/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import com.milaboratory.core.io.util.IOTestUtil;
import com.milaboratory.test.TestUtil;
import com.milaboratory.util.TempFileManager;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UtilTest {
    @Test
    public void testAtomicAppend1() throws Exception {
        File file = TempFileManager.getTempFile();
        Util.appendAtomically(file, "ATTAG".getBytes());
        Util.appendAtomically(file, "GACAG".getBytes());
        byte[] bytes = Files.readAllBytes(file.toPath());
        Assert.assertEquals("ATTAGGACAG", new String(bytes));
    }

    // No way to make single JVM concurrent write test for appendAtomically

    @Ignore
    @Test
    public void testColumns1() throws Exception {
        ArrayList<String> left = new ArrayList<>();
        left.add("For the floating-point");
        left.add(" conversions 'e', asas  asasasas");
        left.add(" 'E', and 'f', asasas asas as");
        left.add(" the ");
        left.add("precision is the number of digits after the decimal separator. If the conversion is");

        ArrayList<String> right = new ArrayList<>();
        right.add("the floating-point");
        right.add("asas  asasasas");
        right.add(" 'E', and 'f', asasas asas as");
        right.add(" the precision is the numberprecision is the number of digits  of digits ");
        right.add("precision is the number of digits after the decimal separator. If the conversion is");


        System.out.println(
                Util.printTwoColumns(
                        left,
                        right,
                        22, 22, 4));
    }
}
