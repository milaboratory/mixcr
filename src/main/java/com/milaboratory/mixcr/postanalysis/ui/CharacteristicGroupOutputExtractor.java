package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupSummary.Simple.class, name = "summary"),
        @JsonSubTypes.Type(value = GroupSummary.VJUsage.class, name = "vjUsage"),
        @JsonSubTypes.Type(value = GroupMelt.VJUsageMelt.class, name = "vjUsageMelt"),
        @JsonSubTypes.Type(value = OverlapSummary.class, name = "overlapSummary"),
})
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public interface CharacteristicGroupOutputExtractor<K> {
    Map<Object, OutputTable> getTables(CharacteristicGroupResult<K> result);
}
