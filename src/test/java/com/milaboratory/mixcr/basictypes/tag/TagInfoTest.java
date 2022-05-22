package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.test.TestUtil;
import org.junit.Test;

public class TagInfoTest {
    @Test
    public void test1() {
        TestUtil.assertJson(new TagInfo(TagType.SampleTag, TagValueType.SequenceAndQuality, "TEST", 2));
        TestUtil.assertJson(new TagsInfo(false, new TagInfo(TagType.SampleTag, TagValueType.SequenceAndQuality, "TEST", 2)));
    }
}