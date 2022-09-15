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
package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import io.repseq.core.Chains;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class ChainsFilter<T extends VDJCObject> implements Predicate<T> {
    @JsonProperty("chains")
    public final Set<Chains> chains;
    @JsonProperty("allowChimeras")
    public final boolean allowChimeras;

    @JsonCreator
    public ChainsFilter(@JsonProperty("chains") Set<Chains> chains,
                        @JsonProperty("allowChimeras") boolean allowChimeras) {
        this.chains = chains;
        this.allowChimeras = allowChimeras;
    }

    @Override
    public boolean test(VDJCObject o) {
        Chains chain = o.commonTopChains();
        if (chain.isEmpty() && allowChimeras)
            return true;
        return chains.contains(chain);
    }

    @Override
    public String toString() {
        return allowChimeras ? "chimeras" : chains.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChainsFilter<?> that = (ChainsFilter<?>) o;
        return allowChimeras == that.allowChimeras && Objects.equals(chains, that.chains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chains, allowChimeras);
    }

    /**
     * Parse a set of chains
     */
    public static Set<Chains> parseChainsList(Set<String> chains) {
        ChainsFilter<VDJCObject> f = parseFilter(chains, false);
        return f.chains;
    }

    /**
     * Parse list of chains or chimeras.
     */
    public static <T extends VDJCObject> ChainsFilter<T> parseFilter(Set<String> chains) {
        return parseFilter(chains, true);
    }

    /**
     * Parse list of chains or chimeras.
     */
    private static <T extends VDJCObject> ChainsFilter<T> parseFilter(Set<String> chains, boolean parseChimeras) {
        Set<Chains> result = new HashSet<>(chains.size());
        boolean allowChimeras = false;
        for (String c : chains) {
            switch (c.toLowerCase()) {
                case "chimera":
                case "chimeras":
                    if (!parseChimeras)
                        throw new IllegalArgumentException("Chimeras are not allowed");
                    allowChimeras = true;
                    break;
                case "ig":
                case "tr":
                case "tcr":
                case "all":
                    throw new IllegalArgumentException("Abbreviations are not allowed, please use coma to list export chains" +
                            " (e.g. IGH,IGK,IGL to export all immunoglobulins).");
                default:
                    result.add(Chains.parse(c));
            }
        }
        return new ChainsFilter<>(result, allowChimeras);
    }
}
