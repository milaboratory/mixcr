package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.test.TestUtil;
import org.junit.Test;

public class TagInfoTest {
    @Test
    public void test1() {
        TestUtil.assertJson(new TagInfo(TagType.Sample, TagValueType.SequenceAndQuality, "TEST", 2));
        TestUtil.assertJson(new TagsInfo(12, new TagInfo(TagType.Sample, TagValueType.SequenceAndQuality, "TEST", 2)));
    }
}