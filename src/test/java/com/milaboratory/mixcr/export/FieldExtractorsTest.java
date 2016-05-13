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
package com.milaboratory.mixcr.export;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.Util;
import io.repseq.reference.GeneType;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.ListIterator;

public class FieldExtractorsTest {
    @Ignore
    @Test
    public void bestHits() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Hit\",\n" +
                    "            header = \"Best " + type + " hit\",\n" +
                    "            description = \"Export best " + type + " hit\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_BEST_" + u + "_HIT = extractBestHit(GeneType." + type + ");");
            System.out.println();
        }
    }

    @Ignore
    @Test
    public void hits() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Hits\",\n" +
                    "            header = \"" + type + " hits\",\n" +
                    "            description = \"Export " + type + " hits\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_" + u + "_HITS = extractHits(GeneType." + type + ");");
            System.out.println();
        }
    }

    @Ignore
    @Test
    public void bestAlignments() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Alignment\",\n" +
                    "            header = \"Best " + type + " alignment\",\n" +
                    "            description = \"Export best " + type + " alignment\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_BEST_" + u + "_ALIGNMENT = extractBestAlignments(GeneType." + type + ");");
            System.out.println();
        }
    }

    @Ignore
    @Test
    public void alignments() throws Exception {
        for (GeneType type : GeneType.values()) {
            String u = type.name().substring(0, 1).toUpperCase();
            String l = u.toLowerCase();

            System.out.println("@ExtractorInfo(type = VDJCObject.class,\n" +
                    "            command = \"-" + l + "Alignments\",\n" +
                    "            header = \"" + type + " alignments\",\n" +
                    "            description = \"Export " + type + " alignments\")");
            System.out.println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_" + u + "_ALIGNMENTS = extractAlignments(GeneType." + type + ");");
            System.out.println();
        }
    }

    //@Test
    //public void testDescription() throws Exception {
    //    ArrayList<String>[] description = FieldExtractors.getDescription(Clone.class);
    //    System.out.println(Util.printTwoColumns(description[0], description[1], 15, 40, 10, "\n"));
    //}

    @Ignore
    @Test
    public void testName() throws Exception {
        ArrayList<String>[] cols = FieldExtractors.getDescription(Clone.class);
        ListIterator<String>[] iterators = new ListIterator[]{cols[0].listIterator(), cols[1].listIterator()};
        while (iterators[0].hasNext()){
            iterators[0].set("| " + remEx(iterators[0].next()));
            iterators[1].set("| " + remEx(iterators[1].next()) + " |");
        }
        System.out.println(Util.printTwoColumns(cols[0],cols[1],25,160,2));


    }

    static String remEx(String str){
        String replace = str.trim().replace("Export", "").trim();
        replace = String.valueOf(replace.charAt(0)).toUpperCase() + replace.substring(1);
        return replace;
    }
}