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
package com.milaboratory.mixcr.postanalysis.ui;


import com.milaboratory.mitool.tag.TagInfo;
import com.milaboratory.mitool.tag.TagType;
import com.milaboratory.mitool.tag.TagValueType;
import com.milaboratory.mitool.tag.TagsInfo;
import org.junit.Test;

import java.util.Arrays;

public class DownsamplingParametersTest {
    @Test
    public void test1() {
        DownsamplingParameters.parse(
                "count-umi-auto",
                new TagsInfo(2, Arrays.asList(new TagInfo(TagType.Molecule, TagValueType.NonSequence, "UMI", 0)), false),
                false,
                true
        );
    }

    @Test
    public void test2() {
        DownsamplingParameters.parse(
                "top-reads-1000",
                null,
                false,
                true
        );
    }

    @Test
    public void test3() {
        DownsamplingParameters.parse(
                "cumtop-reads-0.5",
                null,
                false,
                true
        );
    }
}
