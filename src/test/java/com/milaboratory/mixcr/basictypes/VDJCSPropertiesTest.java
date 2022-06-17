/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.test.TestUtil;
import com.milaboratory.util.sorting.SortingPropertyRelation;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import org.junit.Assert;
import org.junit.Test;

public class VDJCSPropertiesTest {
    @Test
    public void serializationTest() {
        TestUtil.assertJson(new VDJCSProperties.CloneOrdering(new VDJCSProperties.CloneCount()));
        TestUtil.assertJson(new VDJCSProperties.CloneOrdering(
                new VDJCSProperties.NSequence(GeneFeature.CDR3),
                new VDJCSProperties.VDJCSegment(GeneType.Variable),
                new VDJCSProperties.CloneCount()));
    }

    @Test
    public void relationTest() {
        VDJCSProperties.NSequence ntCDR3 = new VDJCSProperties.NSequence(GeneFeature.CDR3);
        VDJCSProperties.AASequence aaCDR3 = new VDJCSProperties.AASequence(GeneFeature.CDR3);
        VDJCSProperties.NSequence ntVDJRegion = new VDJCSProperties.NSequence(GeneFeature.VDJRegion);
        VDJCSProperties.AASequence aaVDJRegion = new VDJCSProperties.AASequence(GeneFeature.VDJRegion);

        Assert.assertEquals(SortingPropertyRelation.Equal, ntCDR3.relationTo(ntCDR3));
        Assert.assertEquals(SortingPropertyRelation.Sufficient, ntCDR3.relationTo(aaCDR3));
        Assert.assertEquals(SortingPropertyRelation.Necessary, ntCDR3.relationTo(ntVDJRegion));
        Assert.assertEquals(SortingPropertyRelation.None, ntCDR3.relationTo(aaVDJRegion));

        Assert.assertEquals(SortingPropertyRelation.Necessary, aaCDR3.relationTo(ntCDR3));
        Assert.assertEquals(SortingPropertyRelation.Equal, aaCDR3.relationTo(aaCDR3));
        Assert.assertEquals(SortingPropertyRelation.Necessary, aaCDR3.relationTo(ntVDJRegion));
        Assert.assertEquals(SortingPropertyRelation.Necessary, aaCDR3.relationTo(aaVDJRegion));

        Assert.assertEquals(SortingPropertyRelation.Sufficient, ntVDJRegion.relationTo(ntCDR3));
        Assert.assertEquals(SortingPropertyRelation.Sufficient, ntVDJRegion.relationTo(aaCDR3));
        Assert.assertEquals(SortingPropertyRelation.Equal, ntVDJRegion.relationTo(ntVDJRegion));
        Assert.assertEquals(SortingPropertyRelation.Sufficient, ntVDJRegion.relationTo(aaVDJRegion));

        Assert.assertEquals(SortingPropertyRelation.None, aaVDJRegion.relationTo(ntCDR3));
        Assert.assertEquals(SortingPropertyRelation.Sufficient, aaVDJRegion.relationTo(aaCDR3));
        Assert.assertEquals(SortingPropertyRelation.Necessary, aaVDJRegion.relationTo(ntVDJRegion));
        Assert.assertEquals(SortingPropertyRelation.Equal, aaVDJRegion.relationTo(aaVDJRegion));
    }
}