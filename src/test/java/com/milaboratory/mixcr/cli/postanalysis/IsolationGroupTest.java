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
package com.milaboratory.mixcr.cli.postanalysis;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.Chains;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class IsolationGroupTest {
    @Test
    public void test1() throws JsonProcessingException {
        IsolationGroup expected = IsolationGroup.invoke(Chains.TRA_NAMED, new HashMap<>());
        String str = GlobalObjectMappers.getPretty().writeValueAsString(expected);
        IsolationGroup actual = GlobalObjectMappers.getPretty().readValue(str, IsolationGroup.class);
        Assert.assertEquals(expected, actual);
    }
}
