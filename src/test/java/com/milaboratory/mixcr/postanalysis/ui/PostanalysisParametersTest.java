package com.milaboratory.mixcr.postanalysis.ui;


import com.milaboratory.mixcr.basictypes.tag.TagInfo;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagValueType;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import org.junit.Test;

public class PostanalysisParametersTest {
    @Test
    public void test1() {
        PostanalysisParameters.parseDownsampling(
                "count-umi-auto",
                new TagsInfo(2, new TagInfo(TagType.Molecule, TagValueType.ByteString, "UMI", 1)),
                false
        );
    }

    @Test
    public void test2() {
        PostanalysisParameters.parseDownsampling(
                "top-reads-1000",
                null,
                false
        );
    }

    @Test
    public void test3() {
        PostanalysisParameters.parseDownsampling(
                "cumtop-reads-0.5",
                null,
                false
        );
    }
}