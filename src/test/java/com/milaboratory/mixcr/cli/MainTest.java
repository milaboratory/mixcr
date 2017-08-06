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

import org.junit.Ignore;
import org.junit.Test;

public class MainTest {
//    @Test
//    public void testMain1() throws Exception {
//        String input = Test.class.getClassLoader().getResource("sequences/sample_IGH_R1.fastq").getFile();
//        String tempDir = System.getProperty("java.io.tmpdir");
//        Main.main("align", input, System.getProperty("java.io.tmpdir") + "/out.vdjca");
//    }
//
//    @Test
//    public void testMain2() throws Exception {
//        String input = System.getProperty("java.io.tmpdir") + "/out.vdjca";
//        String tempDir = System.getProperty("java.io.tmpdir");
//        Main.main("assemble", input, tempDir + "hui", "-t", "3");
//    }
//
    @Ignore
    @Test
    public void testMain3() throws Exception {
        //String input = System.getProperty("java.io.tmpdir") + "/out.vdjca";
        //String tempDir = System.getProperty("java.io.tmpdir");
        //System.out.println(tempDir);
        Main.main("listLibraries");
    }

    @Ignore
    @Test
    public void testMain4() throws Exception {
        Main.main("-h");
    }
}