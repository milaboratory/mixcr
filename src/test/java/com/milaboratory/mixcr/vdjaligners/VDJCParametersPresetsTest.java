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