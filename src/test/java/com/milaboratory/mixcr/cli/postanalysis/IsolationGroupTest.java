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
        IsolationGroup expected = new IsolationGroup(Chains.TRA_NAMED, new HashMap<>());
        String str = GlobalObjectMappers.getPretty().writeValueAsString(expected);
        IsolationGroup actual = GlobalObjectMappers.getPretty().readValue(str, IsolationGroup.class);
        Assert.assertEquals(expected, actual);
    }
}