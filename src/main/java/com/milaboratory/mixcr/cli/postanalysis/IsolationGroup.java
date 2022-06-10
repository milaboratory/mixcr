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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.repseq.core.Chains;
import io.repseq.core.Chains.NamedChains;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Group of samples with specific metadata properties projected onto specific chains. Common downsampling is applied for
 * all samples in the group.
 */
public class IsolationGroup {
    /** Chains */
    @JsonProperty("chains")
    @JsonSerialize(using = Chains.KnownChainsSerializer.class)
    @JsonDeserialize(using = Chains.KnownChainsDeserializer.class)
    final NamedChains chains;
    /** Metadata field=value; always sorted by key */
    @JsonProperty("groups")
    final Map<String, Object> group;

    @JsonCreator
    public IsolationGroup(@JsonProperty("chains") NamedChains chains,
                          @JsonProperty("groups") Map<String, Object> group) {
        this.chains = chains;
        this.group = group == null ? null : sortByKey(group);
    }

    private static LinkedHashMap<String, Object> sortByKey(Map<String, Object> group) {
        return group.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, __) -> a, LinkedHashMap::new
        ));
    }

    public String toString(boolean withChains) {
        String str = group.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
        if (withChains)
            return "chains=" + chains.name + (str.isEmpty() ? "" : "," + str);
        else
            return str;
    }

    @Override
    public String toString() {
        return toString(true);
    }

    /**
     * Generate file extension for specific group
     */
    public String extension() {
        StringBuilder sb = new StringBuilder();
        sb.append(".").append(chains.name);
        for (Object v : group.values()) {
            if (!v.toString().equalsIgnoreCase(chains.name))
                sb.append(".").append(v);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IsolationGroup that = (IsolationGroup) o;
        return Objects.equals(chains, that.chains) && Objects.equals(group, that.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chains, group);
    }
}
