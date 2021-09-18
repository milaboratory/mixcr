/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.io.util.IOTestUtil;
import org.junit.Test;

/**
 * Created by poslavsky on 15/04/15.
 */
public class VDJCParametersPresetsTest {
    @Test
    public void test1() throws Exception {
        IOTestUtil.assertJavaSerialization(VDJCParametersPresets.getByName("default"));
    }
}
