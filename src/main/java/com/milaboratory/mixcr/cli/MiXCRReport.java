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
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerReport;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerReport;
import com.milaboratory.mixcr.trees.SHMTreeSourceFileReport;
import com.milaboratory.mixcr.util.VDJCObjectExtenderReport;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.Report;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AlignerReport.class, name = "alignerReport"),
        @JsonSubTypes.Type(value = ChainUsageStats.class, name = "chainUsage"),
        @JsonSubTypes.Type(value = CloneAssemblerReport.class, name = "assemblerReport"),
        @JsonSubTypes.Type(value = FullSeqAssemblerReport.class, name = "fullSeqAssemblerReport"),
        @JsonSubTypes.Type(value = PartialAlignmentsAssemblerReport.class, name = "partialAlignmentsAssemblerReport"),
        @JsonSubTypes.Type(value = PreCloneAssemblerReport.class, name = "preCloneAssemblerReport"),
        @JsonSubTypes.Type(value = ReadTrimmerReport.class, name = "readTrimmerReport"),
        @JsonSubTypes.Type(value = TagReport.class, name = "tagReport"),
        @JsonSubTypes.Type(value = VDJCObjectExtenderReport.class, name = "extenderReport"),
        @JsonSubTypes.Type(value = RefineTagsAndSortReport.class, name = "refineTagsAndSort"),
        @JsonSubTypes.Type(value = SHMTreeSourceFileReport.class, name = "SHMTreeSourceFileReport"),
        @JsonSubTypes.Type(value = BuildSHMTreeReport.class, name = "buildSHMTreeReport")
})
@Serializable(asJson = true)
public interface MiXCRReport extends Report {
}
