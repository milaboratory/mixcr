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
package com.milaboratory.mixcr.postanalysis.additive;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunction;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions.VJGenes;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKey;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

/**
 *
 */
public final class AdditiveCharacteristics {
    private AdditiveCharacteristics() {}

    public static final String readCountKey = "readCount";

    public static AdditiveCharacteristic<String, Clone> readCount(SetPreprocessorFactory<Clone> preproc) {
        return new AdditiveCharacteristic<>(readCountKey, preproc,
                new WeightFunctions.Count(),
                new KeyFunctions.Named<>(readCountKey),
                new AdditiveMetrics.Constant(),
                AggregationType.Sum,
                false);
    }

    public static final String cloneCountKey = "cloneCount";

    public static AdditiveCharacteristic<String, Clone> clonotypeCount(SetPreprocessorFactory<Clone> preproc) {
        return new AdditiveCharacteristic<>(cloneCountKey, preproc,
                new WeightFunctions.NoWeight<>(),
                new KeyFunctions.Named<>(cloneCountKey),
                new AdditiveMetrics.Constant(),
                AggregationType.Sum,
                false);
    }

    public static AdditiveCharacteristic<String, Clone> weightedLengthOf(GeneFeature gf, boolean aa) {
        return weightedLengthOf(NoPreprocessing.factory(), gf, aa);
    }

    public static AdditiveCharacteristic<String, Clone> weightedLengthOf(SetPreprocessorFactory<Clone> preproc, GeneFeature gf, boolean aa) {
        return lengthOf(preproc, WeightFunctions.Count, gf, aa);
    }

    public static AdditiveCharacteristic<String, Clone> lengthOf(SetPreprocessorFactory<Clone> preproc, WeightFunction<Clone> wt, GeneFeature gf, boolean aa) {
        String name = "Length of " + GeneFeature.encode(gf) + ", " + (aa ? "aa" : "nt");
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                wt,
                new KeyFunctions.Named<>(name),
                new AdditiveMetrics.GeneFeatureLength<>(gf, aa),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> weightedAddedNucleotides(SetPreprocessorFactory<Clone> preproc) {
        return addedNucleotides(preproc, WeightFunctions.Count);
    }


    public static AdditiveCharacteristic<String, Clone> addedNucleotides(SetPreprocessorFactory<Clone> preproc, WeightFunction<Clone> wt) {
        String name = "Added nucleotides";
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                wt,
                new KeyFunctions.Named<>(name),
                new AdditiveMetrics.AddedNucleotides<>(),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> weightedBbiophysicsNormalized(AAProperties.AAProperty property, GeneFeature gf) {
        return weightedBbiophysicsNormalized(NoPreprocessing.factory(), property, gf);
    }

    public static AdditiveCharacteristic<String, Clone> weightedBbiophysicsNormalized(SetPreprocessorFactory<Clone> preproc, AAProperties.AAProperty property, GeneFeature gf) {
        return biophysicsNormalized(preproc, WeightFunctions.Count, property, gf);
    }

    public static AdditiveCharacteristic<String, Clone> biophysicsNormalized(SetPreprocessorFactory<Clone> preproc,
                                                                             WeightFunction<Clone> wt,
                                                                             AAProperties.AAProperty property,
                                                                             GeneFeature gf) {
        String name = property.name() + " of " + GeneFeature.encode(gf) + " (normalized)";
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                wt,
                new KeyFunctions.Named<>(name),
                new AdditiveMetrics.AAPropertyNormalized(property, gf),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> weightedBiophysics(AAProperties.AAProperty property, GeneFeature gf, AAProperties.Adjustment adjustment, int nLetters) {
        return weightedBiophysics(NoPreprocessing.factory(), property, gf, adjustment, nLetters);
    }

    public static AdditiveCharacteristic<String, Clone> weightedBiophysics(SetPreprocessorFactory<Clone> preproc, AAProperties.AAProperty property, GeneFeature gf, AAProperties.Adjustment adjustment, int nLetters) {
        return biophysics(preproc, WeightFunctions.Count, property, gf, adjustment, nLetters);
    }

    public static AdditiveCharacteristic<String, Clone> biophysics(SetPreprocessorFactory<Clone> preproc,
                                                                   WeightFunction<Clone> wt,
                                                                   AAProperties.AAProperty property,
                                                                   GeneFeature gf,
                                                                   AAProperties.Adjustment adjustment,
                                                                   int nLetters) {
        String name = property.name() + " of " + GeneFeature.encode(gf);
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                wt,
                new KeyFunctions.Named<>(name),
                new AdditiveMetrics.AAPropertySum(property, gf, adjustment, nLetters),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(GeneType geneType, KeyFunction<String, Clone> keyFunction) {
        return segmentUsage(NoPreprocessing.factory(), geneType, keyFunction);
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(SetPreprocessorFactory<Clone> preproc, GeneType geneType, KeyFunction<String, Clone> keyFunction) {
        return segmentUsage(preproc, WeightFunctions.Count, geneType, keyFunction);
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(SetPreprocessorFactory<Clone> preproc,
                                                                     WeightFunction<Clone> wt,
                                                                     GeneType geneType,
                                                                     KeyFunction<String, Clone> keyFunction) {
        String name = geneType.getLetter() + "-usage";
        return new AdditiveCharacteristic<>(
                name,
                preproc,
                wt,
                keyFunction,
                new AdditiveMetrics.Constant(1),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(GeneType geneType) {
        return segmentUsage(NoPreprocessing.factory(), geneType);
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(SetPreprocessorFactory<Clone> preproc, GeneType geneType) {
        return segmentUsage(preproc, geneType, new KeyFunctions.SegmentUsage<>(geneType))
                .setName(geneType.getLetter() + "SegmentUsage");
    }

    public static AdditiveCharacteristic<String, Clone> segmentUsage(SetPreprocessorFactory<Clone> preproc,
                                                                     WeightFunction<Clone> wt,
                                                                     GeneType geneType) {
        return segmentUsage(preproc, wt, geneType, new KeyFunctions.SegmentUsage<>(geneType))
                .setName(geneType.getLetter() + "SegmentUsage");
    }

    public static AdditiveCharacteristic<String, Clone> geneUsage(GeneType geneType) {
        return geneUsage(NoPreprocessing.factory(), geneType);
    }

    public static AdditiveCharacteristic<String, Clone> geneUsage(SetPreprocessorFactory<Clone> preproc, GeneType geneType) {
        return segmentUsage(preproc, geneType, new KeyFunctions.GeneUsage<>(geneType))
                .setName(geneType.getLetter() + "GeneUsage");
    }

    public static AdditiveCharacteristic<String, Clone> familyUsage(GeneType geneType) {
        return familyUsage(NoPreprocessing.factory(), WeightFunctions.Count, geneType);
    }

    public static AdditiveCharacteristic<String, Clone> familyUsage(SetPreprocessorFactory<Clone> preproc,
                                                                    WeightFunction<Clone> wt,
                                                                    GeneType geneType) {
        return segmentUsage(preproc, wt, geneType, new KeyFunctions.FamiltyUsage<>(geneType))
                .setName(geneType.getLetter() + "FamilyUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjUsage(KeyFunction<VJGenes<String>, Clone> keyFunction) {
        return vjUsage(NoPreprocessing.factory(), keyFunction);
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjUsage(SetPreprocessorFactory<Clone> preproc, KeyFunction<VJGenes<String>, Clone> keyFunction) {
        return vjUsage(preproc, WeightFunctions.Count, keyFunction);
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjUsage(SetPreprocessorFactory<Clone> preproc,
                                                                         WeightFunction<Clone> wt,
                                                                         KeyFunction<VJGenes<String>, Clone> keyFunction) {
        return new AdditiveCharacteristic<>(
                "VJUsage",
                preproc,
                wt,
                keyFunction,
                new AdditiveMetrics.Constant(1),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjSegmentUsage() {
        return vjSegmentUsage(NoPreprocessing.factory());
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjSegmentUsage(SetPreprocessorFactory<Clone> preproc) {
        return vjUsage(preproc, new KeyFunctions.VJSegmentUsage<>()).setName("VJSegmentUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjSegmentUsage(SetPreprocessorFactory<Clone> preproc,
                                                                                WeightFunction<Clone> wt) {
        return vjUsage(preproc, wt, new KeyFunctions.VJSegmentUsage<>()).setName("VJSegmentUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjGeneUsage() {
        return vjGeneUsage(NoPreprocessing.factory());
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjGeneUsage(SetPreprocessorFactory<Clone> preproc) {
        return vjUsage(preproc, new KeyFunctions.VJGeneUsage<>()).setName("VJGeneUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjFamilyUsage() {
        return vjFamilyUsage(NoPreprocessing.factory());
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjFamilyUsage(SetPreprocessorFactory<Clone> preproc,
                                                                               WeightFunction<Clone> wt) {
        return vjUsage(preproc, wt, new KeyFunctions.VJFamilyUsage<>()).setName("VJFamilyUsage");
    }

    public static AdditiveCharacteristic<VJGenes<String>, Clone> vjFamilyUsage(SetPreprocessorFactory<Clone> preproc) {
        return vjUsage(preproc, new KeyFunctions.VJFamilyUsage<>()).setName("VJFamilyUsage");
    }

    public static AdditiveCharacteristic<KeyFunctions.Isotype, Clone> isotypeUsage() {
        return isotypeUsage(NoPreprocessing.factory());
    }

    public static AdditiveCharacteristic<KeyFunctions.Isotype, Clone> isotypeUsage(SetPreprocessorFactory<Clone> preproc) {
        return isotypeUsage(preproc, WeightFunctions.Count);
    }

    public static AdditiveCharacteristic<KeyFunctions.Isotype, Clone> isotypeUsage(SetPreprocessorFactory<Clone> preproc, WeightFunction<Clone> wt) {
        return new AdditiveCharacteristic<>(
                "IsotypeUsage",
                preproc,
                wt,
                new KeyFunctions.IsotypeUsage<>(),
                new AdditiveMetrics.Constant(1),
                AggregationType.Mean,
                false
        );
    }

    public static AdditiveCharacteristic<SpectratypeKey<String>, Clone> VSpectratype() {
        return VSpectratype(NoPreprocessing.factory());
    }

    public static AdditiveCharacteristic<SpectratypeKey<String>, Clone> VSpectratype(SetPreprocessorFactory<Clone> preproc) {
        return VSpectratype(preproc, WeightFunctions.Count);
    }

    public static AdditiveCharacteristic<SpectratypeKey<String>, Clone> VSpectratype(SetPreprocessorFactory<Clone> preproc,
                                                                                     WeightFunction<Clone> wt) {
        return new AdditiveCharacteristic<>("VSpectratype",
                preproc,
                wt,
                new SpectratypeKeyFunction<>(new KeyFunctions.SegmentUsage<>(GeneType.Variable), GeneFeature.CDR3, false),
                new AdditiveMetrics.Constant(), AggregationType.Sum, false);
    }

    public static AdditiveCharacteristic<String, Clone> VSpectratypeMean(SetPreprocessorFactory<Clone> preproc) {
        return VSpectratypeMean(preproc, WeightFunctions.Count);
    }

    public static AdditiveCharacteristic<String, Clone> VSpectratypeMean(SetPreprocessorFactory<Clone> preproc,
                                                                         WeightFunction<Clone> wt) {
        return new AdditiveCharacteristic<>(
                "VSpectratypeMean",
                preproc,
                wt,
                new KeyFunctions.SegmentUsage<>(GeneType.Variable),
                new AdditiveMetrics.GeneFeatureLength<>(GeneFeature.CDR3, false),
                AggregationType.Mean,
                true
        );
    }
}
