package com.milaboratory.mixcr.tags;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

/**
 *
 */
public class CloneTagTupleFilterTest {

    @Test
    public void name() throws JsonProcessingException {
        System.out.println(GlobalObjectMappers.PRETTY.writeValueAsString(new CloneTagTupleFilter(0, 0, 0, 0, 0, 0, 0.0 / 0.0, 0)));
    }
}
