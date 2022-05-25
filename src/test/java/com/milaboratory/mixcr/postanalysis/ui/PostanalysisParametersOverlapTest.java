package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

public class PostanalysisParametersOverlapTest {
    @Test
    public void test1() throws JsonProcessingException {
        PostanalysisParametersOverlap par = new PostanalysisParametersOverlap();
        System.out.println(GlobalObjectMappers.getPretty().writeValueAsString(par));
    }
}