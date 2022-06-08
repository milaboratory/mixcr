package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.test.TestUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class TagsInfoTest {
    @Test
    public void testJson() {
        TagsInfo ti = new TagsInfo(1,
                new TagInfo(TagType.Sample, TagValueType.SequenceAndQuality,"SPL1", 0),
                new TagInfo(TagType.Cell, TagValueType.SequenceAndQuality,"CELL", 1),
                new TagInfo(TagType.Molecule, TagValueType.SequenceAndQuality,"UMI", 2)
        );
        TestUtil.assertJson(ti);
    }
}