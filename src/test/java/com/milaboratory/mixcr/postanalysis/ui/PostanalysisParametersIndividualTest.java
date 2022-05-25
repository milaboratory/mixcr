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