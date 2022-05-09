package com.milaboratory.mixcr.cli.postanalysis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema;

/**
 * PA results for isolation group.
 */
@JsonAutoDetect
public final class PaResultByGroup {
    @JsonProperty("isolationGroup")
    public final IsolationGroup group;
    @JsonProperty("schema")
    public final PostanalysisSchema<?> schema;
    @JsonProperty("result")
    public final PostanalysisResult result;

    @JsonCreator
    public PaResultByGroup(@JsonProperty("isolationGroup") IsolationGroup group,
                           @JsonProperty("schema") PostanalysisSchema<?> schema,
                           @JsonProperty("result") PostanalysisResult result) {
        this.group = group;
        this.schema = schema;
        this.result = result;
    }
}
