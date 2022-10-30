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
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mitool.helpers.KObjectMapperProvider;
import com.milaboratory.mixcr.alleles.FindAllelesReport;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerReport;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerReport;
import com.milaboratory.mixcr.trees.BuildSHMTreeReport;
import com.milaboratory.mixcr.util.VDJCObjectExtenderReport;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.Report;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @Type(value = AlignerReport.class, name = "alignerReport"),
        @Type(value = ChainUsageStats.class, name = "chainUsage"),
        @Type(value = CloneAssemblerReport.class, name = "assemblerReport"),
        @Type(value = FullSeqAssemblerReport.class, name = "fullSeqAssemblerReport"),
        @Type(value = PartialAlignmentsAssemblerReport.class, name = "partialAlignmentsAssemblerReport"),
        @Type(value = PreCloneAssemblerReport.class, name = "preCloneAssemblerReport"),
        @Type(value = ReadTrimmerReport.class, name = "readTrimmerReport"),
        @Type(value = TagReport.class, name = "tagReport"),
        @Type(value = VDJCObjectExtenderReport.class, name = "extenderReport"),
        @Type(value = RefineTagsAndSortReport.class, name = "refineTagsAndSort"),
        @Type(value = FindAllelesReport.class, name = "findAllelesReport"),
        @Type(value = BuildSHMTreeReport.class, name = "buildSHMTreeReport")
})
@Serializable(asJson = true, objectMapperBy = KObjectMapperProvider.class)
public interface MiXCRReport extends Report {
}
