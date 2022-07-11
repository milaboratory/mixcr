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