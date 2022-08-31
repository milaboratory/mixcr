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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

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
