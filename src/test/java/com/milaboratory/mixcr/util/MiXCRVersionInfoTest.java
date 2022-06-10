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
package com.milaboratory.mixcr.util;

import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class MiXCRVersionInfoTest {
    @Test
    public void testSerialize1() throws IOException {
        MiXCRVersionInfo version = MiXCRVersionInfo.get();
        String versionString = version.getVersionString(AppVersionInfo.OutputType.ToConsole, true);
        Assert.assertTrue(versionString.contains("RepSeq.IO"));
        Assert.assertTrue(versionString.contains("MiLib"));
        Assert.assertTrue(versionString.contains("MiXCR"));
        String pretty = GlobalObjectMappers.getPretty().writeValueAsString(version);
        MiXCRVersionInfo v = GlobalObjectMappers.getPretty().readValue(pretty, MiXCRVersionInfo.class);
        assertEquals(version, v);
    }
}
