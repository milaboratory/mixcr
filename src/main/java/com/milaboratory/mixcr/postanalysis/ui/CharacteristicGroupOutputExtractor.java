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
        @JsonSubTypes.Type(value = GroupSummary.class, name = "summary"),
        @JsonSubTypes.Type(value = OverlapSummary.class, name = "overlapSummary"),
        @JsonSubTypes.Type(value = GroupMelt.VJUsageMelt.class, name = "vjUsage")
})
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public interface CharacteristicGroupOutputExtractor<K> {
    Map<Object, OutputTable> getTables(CharacteristicGroupResult<K> result);
}
