/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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