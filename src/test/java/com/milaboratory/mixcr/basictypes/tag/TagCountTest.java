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
package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mitool.tag.SequenceTagValue;
import com.milaboratory.mitool.tag.TagValue;
import org.junit.Assert;
import org.junit.Test;

public class TagCountTest {
    static TagTuple tt(String... vals) {
        TagValue[] values = new TagValue[vals.length];
        for (int i = 0; i < vals.length; i++)
            values[i] = new SequenceTagValue(new NucleotideSequence(vals[i]));
        return new TagTuple(values);
    }

    @Test
    public void test1() {
        TagCountAggregator tca1 = new TagCountAggregator();
        tca1.add(tt("A", "T"), 1);
        tca1.add(tt("A", "G"), 3);
        tca1.add(tt("T", "T"), 4);
        tca1.add(tt("G", "T"), 5);
        TagCount tc1 = tca1.createAndDestroy();
        Assert.assertEquals(4, tc1.getTagDiversity(2));
        Assert.assertEquals(3, tc1.getTagDiversity(1));
        Assert.assertEquals(1, tc1.getTagDiversity(0));

        TagCountAggregator tca2 = new TagCountAggregator();
        tca2.add(tt("A"), 2);
        tca2.add(tt("T"), 1);
        tca2.add(tt("G"), 1);
        TagCount tc2 = tca2.createAndDestroy();
        Assert.assertEquals(tc2, tc1.reduceToLevel(1));
    }
}
