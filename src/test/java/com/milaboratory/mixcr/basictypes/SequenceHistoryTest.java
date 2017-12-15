/*
 * Copyright (c) 2014-2017, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.test.TestUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SequenceHistoryTest {
    @Test
    public void testIO1() throws Exception {
        List<SequenceHistory> entries = new ArrayList<>();
        SequenceHistory.RawSequence r1 = new SequenceHistory.RawSequence(123151243L, (byte) 1, false, 100);
        SequenceHistory.RawSequence r2 = new SequenceHistory.RawSequence(0L, (byte) 0, true, 100);
        entries.add(r1);
        entries.add(r2);
        entries.add(new SequenceHistory.Extend(r1, 10, 20));
        entries.add(new SequenceHistory.Merge(SequenceHistory.OverlapType.CDR3Overlap,
                new SequenceHistory.Extend(r1, 10, 20), r2, 12, 1));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrimitivO o = new PrimitivO(bos);

        for (SequenceHistory entry : entries)
            o.writeObject(entry);

        PrimitivI i = new PrimitivI(new ByteArrayInputStream(bos.toByteArray()));
        for (SequenceHistory entry : entries)
            Assert.assertEquals(entry, i.readObject(SequenceHistory.class));

        for (SequenceHistory entry : entries)
            TestUtil.assertJson(entry, SequenceHistory.class);
    }

    @Test
    public void testPositions1() throws Exception {
        SequenceHistory.RawSequence r1 = new SequenceHistory.RawSequence(100, (byte) 0, false, 100);
        SequenceHistory.Extend e1 = new SequenceHistory.Extend(r1, 3, 4);
        SequenceHistory.RawSequence r2 = new SequenceHistory.RawSequence(100, (byte) 1, true, 100);

        SequenceHistory.Merge m1 = new SequenceHistory.Merge(SequenceHistory.OverlapType.CDR3Overlap, e1, r2, 97, 1);
        SequenceHistory.Merge m2 = new SequenceHistory.Merge(SequenceHistory.OverlapType.CDR3Overlap, e1, r2, -91, 1);

        Assert.assertEquals(10, m1.overlap());
        Assert.assertEquals(9, m2.overlap());

        Assert.assertEquals(197, m1.length());
        Assert.assertEquals(198, m2.length());

        Assert.assertNull(m1.offset(new SequenceHistory.FullReadIndex(100, (byte) 0, true)));
        Assert.assertNull(m1.offset(new SequenceHistory.FullReadIndex(99, (byte) 0, false)));

        Assert.assertEquals((Object) 3, m1.offset(r1.index));
        Assert.assertEquals((Object) 97, m1.offset(r2.index));

        Assert.assertEquals((Object) 94, m2.offset(r1.index));
        Assert.assertEquals((Object) 0, m2.offset(r2.index));

        System.out.println(m1);
        System.out.println(m2);
    }
}