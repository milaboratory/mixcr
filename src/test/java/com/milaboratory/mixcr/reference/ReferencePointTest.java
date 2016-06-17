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
package com.milaboratory.mixcr.reference;

import com.milaboratory.mixcr.cli.Util;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class ReferencePointTest {
    @Test
    public void test1() throws Exception {
        ReferencePoint[] pointsToTest = new ReferencePoint[]{ReferencePoint.V5UTRBeginTrimmed,
                ReferencePoint.VEndTrimmed, ReferencePoint.JBeginTrimmed};
        for (ReferencePoint referencePoint : pointsToTest)
            Assert.assertNotNull(referencePoint.getActivationPoint());
    }

    @Test
    public void printDocs() throws Exception {
        List<String> names = new ArrayList<>(), docs = new ArrayList<>();
        int maxName = 0, maxDoc = 0;
        for (Field field : ReferencePoint.class.getFields()) {
            if (!field.isAnnotationPresent(ReferencePoint.Doc.class))
                continue;
            String name = "| ``" + field.getName() + "``";
            String doc = field.getAnnotation(ReferencePoint.Doc.class).value();
            maxName = Math.max(maxName, name.length());
            maxDoc = Math.max(maxDoc, doc.length());
            names.add(name);
            docs.add(doc);
        }

        final ListIterator<String> it = names.listIterator();
        while (it.hasNext()) {
            String name = it.next();
            int l = (maxName + 3 - name.length());
            for (int i = 0; i < l; i++)
                name += " ";
            name += "|";
            it.set(name);
        }

        final String header = "+-------------------------+-------------------------------------------------------+";
        String x = Util.printTwoColumns(names, docs, maxName + 5, 50, 4, header+"\n");
        x = x.replace("                                ", "|                         |     ");

        System.out.println(header);
        for (String s : x.split("\n")) {
            if (s.endsWith("+")) {
                System.out.println(s);
                continue;
            }
            int l = header.length() - s.length() - 1;
            for (int i = 0; i < l; i++) {
                s += " ";
            }
            s += "|";
            System.out.println(s);
        }
    }
}