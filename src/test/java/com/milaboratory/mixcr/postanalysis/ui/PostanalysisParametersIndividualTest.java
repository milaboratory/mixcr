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


import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class PostanalysisParametersIndividualTest {
    @Test
    public void test1() throws JsonProcessingException {
        PostanalysisParametersIndividual par = new PostanalysisParametersIndividual();
        String str = GlobalObjectMappers.getPretty().writeValueAsString(par);
        Assert.assertEquals(par, GlobalObjectMappers.getPretty().readValue(str, PostanalysisParametersIndividual.class));
    }

    @Test
    public void test2() throws IOException {
        PostanalysisParametersPreset.getByNameIndividual("default");
    }
}