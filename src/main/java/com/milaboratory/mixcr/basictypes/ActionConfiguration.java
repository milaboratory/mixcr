package com.milaboratory.mixcr.basictypes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.cli.ActionAlign;
import com.milaboratory.primitivio.annotations.Serializable;

/**
 * A data structure that holds the whole set of parameters that control (thus uniquely determine) specific MiXCR
 * action.
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
        @JsonSubTypes.Type(value = ActionAlign.AlignConfiguration.class, name = "align-configuration")})
@Serializable(asJson = true)
public interface ActionConfiguration {}
