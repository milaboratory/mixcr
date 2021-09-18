/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.util;

import com.milaboratory.util.GlobalObjectMappers;
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
        String pretty = GlobalObjectMappers.PRETTY.writeValueAsString(version);
        MiXCRVersionInfo v = GlobalObjectMappers.PRETTY.readValue(pretty, MiXCRVersionInfo.class);
        assertEquals(version, v);
    }
}
