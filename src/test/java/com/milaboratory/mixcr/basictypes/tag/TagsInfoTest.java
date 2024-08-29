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

import com.milaboratory.mitool.tag.TagInfo;
import com.milaboratory.mitool.tag.TagType;
import com.milaboratory.mitool.tag.TagValueType;
import com.milaboratory.mitool.tag.TagsInfo;
import com.milaboratory.test.TestUtil;
import org.junit.Test;

import java.util.Arrays;

public class TagsInfoTest {
    @Test
    public void testJson() {
        TagsInfo ti = new TagsInfo(1,
                Arrays.asList(
                        new TagInfo(TagType.Sample, TagValueType.SequenceAndQuality, "SPL1", 0),
                        new TagInfo(TagType.Cell, TagValueType.SequenceAndQuality, "CELL", 1),
                        new TagInfo(TagType.Molecule, TagValueType.SequenceAndQuality, "UMI", 2)
                ),
                false
        );
        TestUtil.assertJson(ti);
    }
}
