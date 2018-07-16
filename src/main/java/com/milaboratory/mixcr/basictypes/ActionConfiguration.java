package com.milaboratory.mixcr.basictypes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.cli.*;
import com.milaboratory.primitivio.annotations.Serializable;

/**
 * A data structure which holds the whole set of parameters which affect specific MiXCR action.
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActionAlign.AlignConfiguration.class, name = "align-configuration"),
        @JsonSubTypes.Type(value = ActionAssemble.AssembleConfiguration.class, name = "assemble-configuration"),
        @JsonSubTypes.Type(value = ActionAssemblePartialAlignments.AssemblePartialConfiguration.class, name = "assemble-partial-configuration"),
        @JsonSubTypes.Type(value = ActionExtend.ExtendConfiguration.class, name = "extend-configuration"),
        @JsonSubTypes.Type(value = ActionMergeAlignments.MergeConfiguration.class, name = "merge-configuration"),
        @JsonSubTypes.Type(value = ActionFilterAlignments.FilterConfiguration.class, name = "filter-configuration"),
        @JsonSubTypes.Type(value = ActionSortAlignments.SortConfiguration.class, name = "sort-configuration"),
        @JsonSubTypes.Type(value = ActionSlice.SliceConfiguration.class, name = "slice-configuration")})
@Serializable(asJson = true)
public interface ActionConfiguration {
    String actionName();
}