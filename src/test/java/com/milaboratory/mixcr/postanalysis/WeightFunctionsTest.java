package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.mixcr.postanalysis.WeightFunctions.TagCount;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Assert;
import org.junit.Test;

public class WeightFunctionsTest {
    @Test
    public void test1() throws JsonProcessingException {
        Object expected = new TagCount(1);

        String str = GlobalObjectMappers.getOneLine().writeValueAsString(expected);
        TagCount actual = GlobalObjectMappers.getOneLine().readValue(str, TagCount.class);

        Assert.assertEquals(expected, actual);
    }
}