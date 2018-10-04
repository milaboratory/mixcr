package com.milaboratory.mixcr.basictypes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.cli.newcli.*;
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
        @JsonSubTypes.Type(value = CommandAlign.AlignConfiguration.class, name = "align-configuration"),
        @JsonSubTypes.Type(value = CommandAssemble.AssembleConfiguration.class, name = "assemble-configuration"),
        @JsonSubTypes.Type(value = CommandAssembleContigs.AssembleContigsConfiguration.class, name = "assemble-contig-configuration"),
        @JsonSubTypes.Type(value = CommandAssemblePartialAlignments.AssemblePartialConfiguration.class, name = "assemble-partial-configuration"),
        @JsonSubTypes.Type(value = CommandExtend.ExtendConfiguration.class, name = "extend-configuration"),
        @JsonSubTypes.Type(value = CommandMergeAlignments.MergeConfiguration.class, name = "merge-configuration"),
        @JsonSubTypes.Type(value = CommandFilterAlignments.FilterConfiguration.class, name = "filter-configuration"),
        @JsonSubTypes.Type(value = CommandSortAlignments.SortConfiguration.class, name = "sort-configuration"),
        @JsonSubTypes.Type(value = CommandSlice.SliceConfiguration.class, name = "slice-configuration")
})
@Serializable(asJson = true)
public interface ActionConfiguration<Conf extends ActionConfiguration<Conf>> {
    String actionName();

    /** Action version string */
    default String versionId() { return ""; }

    /** Compatible with other configuration */
    default boolean compatibleWith(Conf other) { return equals(other); }
}