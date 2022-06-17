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
package com.milaboratory.mixcr.postanalysis.spectratype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunction;
import io.repseq.core.GeneFeature;

import java.util.Objects;

/**
 *
 */
public class SpectratypeKeyFunction<Payload, T extends VDJCObject> implements KeyFunction<SpectratypeKey<Payload>, T> {
    @JsonProperty("payloadFunction")
    public final KeyFunction<Payload, T> payloadFunction;
    @JsonProperty("geneFeature")
    public final GeneFeature geneFeature;
    @JsonProperty("aa")
    public final boolean aa;

    @JsonCreator
    public SpectratypeKeyFunction(@JsonProperty("payloadFunction") KeyFunction<Payload, T> payloadFunction,
                                  @JsonProperty("geneFeature") GeneFeature geneFeature,
                                  @JsonProperty("aa") boolean aa) {
        this.payloadFunction = payloadFunction;
        this.geneFeature = geneFeature;
        this.aa = aa;
    }

    @Override
    public SpectratypeKey<Payload> getKey(T obj) {
        NSequenceWithQuality feature = obj.getFeature(geneFeature);
        if (feature == null)
            return null;
        Payload payload = payloadFunction.getKey(obj);
        if (payload == null)
            return null;

        if (!aa)
            return new SpectratypeKey<>(feature.size(), payload);

        int targetId = obj.getTargetContainingFeature(geneFeature);
        TranslationParameters tr = targetId == -1 ?
                TranslationParameters.FromLeftWithIncompleteCodon
                : obj.getPartitionedTarget(targetId).getPartitioning().getTranslationParameters(geneFeature);
        if (tr == null)
            return null;

        return new SpectratypeKey<>(AminoAcidSequence.translate(feature.getSequence(), tr).size(), payload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpectratypeKeyFunction<?, ?> that = (SpectratypeKeyFunction<?, ?>) o;
        return aa == that.aa &&
                Objects.equals(payloadFunction, that.payloadFunction) &&
                Objects.equals(geneFeature, that.geneFeature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payloadFunction, geneFeature, aa);
    }
}
