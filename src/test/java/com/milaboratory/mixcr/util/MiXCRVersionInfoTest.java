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
