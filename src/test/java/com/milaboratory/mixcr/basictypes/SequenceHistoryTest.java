/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
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
        SequenceHistory.RawSequence r1 = new SequenceHistory.RawSequence(123151243L, (byte) 1, false, 100, 1);
        SequenceHistory.RawSequence r2 = new SequenceHistory.RawSequence(0L, (byte) 0, true, 100, 1);
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
        SequenceHistory.RawSequence r1 = new SequenceHistory.RawSequence(100, (byte) 0, false, 100, 1);
        SequenceHistory.Extend e1 = new SequenceHistory.Extend(r1, 3, 4);
        SequenceHistory.RawSequence r2 = new SequenceHistory.RawSequence(100, (byte) 1, true, 100, 1);

        SequenceHistory.Merge m1 = new SequenceHistory.Merge(SequenceHistory.OverlapType.CDR3Overlap, e1, r2, 97, 1);
        SequenceHistory.Merge m2 = new SequenceHistory.Merge(SequenceHistory.OverlapType.CDR3Overlap, e1, r2, -91, 1);

        Assert.assertEquals(10, m1.overlap());
        Assert.assertEquals(9, m2.overlap());

        Assert.assertEquals(197, m1.getLength());
        Assert.assertEquals(198, m2.getLength());

        Assert.assertNull(m1.offset(new SequenceHistory.FullReadIndex(100, (byte) 0, true)));
        Assert.assertNull(m1.offset(new SequenceHistory.FullReadIndex(99, (byte) 0, false)));

        Assert.assertEquals((Object) 3, m1.offset(r1.getIndex()));
        Assert.assertEquals((Object) 97, m1.offset(r2.getIndex()));

        Assert.assertEquals((Object) 94, m2.offset(r1.getIndex()));
        Assert.assertEquals((Object) 0, m2.offset(r2.getIndex()));

        System.out.println(m1);
        System.out.println(m2);
    }
}
