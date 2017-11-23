/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
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